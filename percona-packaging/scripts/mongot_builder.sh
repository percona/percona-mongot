#!/bin/sh

shell_quote_string() {
  echo "$1" | sed -e 's,\([^a-zA-Z0-9/_.=-]\),\\\1,g'
}

usage () {
    cat <<EOF
Usage: $0 [OPTIONS]
    The following options may be given :
        --builddir=DIR      Absolute path to the dir where all actions will be performed
        --get_sources       Source will be downloaded from github
        --build_src_rpm     If it is set - src rpm will be built
        --build_src_deb     If it is set - source deb package will be built
        --build_rpm         If it is set - rpm will be built
        --build_deb         If it is set - deb will be built
        --build_mongot      If it is set - mongot bazel bundle will be built
        --build_variant     Variant to build(rpm-x64, deb-x64, linux-x64, rpm-aarch64, deb-aarch64, linux-aarch64)
        --install_deps      Install build dependencies(root privilages are required)
        --branch            Branch for build
        --repo              Repo for build
        --version           Version to build

        --help) usage ;;
Example $0 --builddir=/tmp/percona-server-mongodb-mongot --get_sources=1 --build_mongot=1 --build_variant=linux-x64
EOF
        exit 1
}

append_arg_to_args () {
  args="$args "$(shell_quote_string "$1")
}

parse_arguments() {
    pick_args=
    if test "$1" = PICK-ARGS-FROM-ARGV
    then
        pick_args=1
        shift
    fi

    for arg do
        val=$(echo "$arg" | sed -e 's;^--[^=]*=;;')
        case "$arg" in
            --builddir=*) WORKDIR="$val" ;;
            --build_src_rpm=*) SRPM="$val" ;;
            --build_src_deb=*) SDEB="$val" ;;
            --build_rpm=*) RPM="$val" ;;
            --build_deb=*) DEB="$val" ;;
            --get_sources=*) SOURCE="$val" ;;
            --branch=*) BRANCH="$val" ;;
            --repo=*) REPO="$val" ;;
            --version=*) VERSION="$val" ;;
            --install_deps=*) INSTALL="$val" ;;
            --build_mongot=*) MONGOT="$val" ;;
            --build_variant=*) VARIANT="$val" ;;
            --help) usage ;;
            *)
              if test -n "$pick_args"
              then
                  append_arg_to_args "$arg"
              fi
              ;;
        esac
    done
}

check_workdir(){
    if [ "x$WORKDIR" = "x$CURDIR" ]
    then
        echo >&2 "Current directory cannot be used for building!"
        exit 1
    else
        if ! test -d "$WORKDIR"
        then
            echo >&2 "$WORKDIR is not a directory."
            exit 1
        fi
    fi
    return
}

get_sources(){
    cd "${WORKDIR}"
    if [ "${SOURCE}" = 0 ]
    then
        echo "Sources will not be downloaded"
        return 0
    fi
    PRODUCT=percona-server-mongodb-mongot
    echo "PRODUCT=${PRODUCT}" > percona-server-mongodb-mongot.properties
    echo "BUILD_NUMBER=${BUILD_NUMBER}" >> percona-server-mongodb-mongot.properties
    echo "BUILD_ID=${BUILD_ID}" >> percona-server-mongodb-mongot.properties
    echo "VERSION=${VERSION}" >> percona-server-mongodb-mongot.properties
    echo "BRANCH=${BRANCH}" >> percona-server-mongodb-mongot.properties
    rm -rf ${PRODUCT}
    git clone "$REPO" ${PRODUCT}
    retval=$?
    if [ $retval != 0 ]
    then
        echo "There were some issues during repo cloning from github. Please retry one more time"
        exit 1
    fi
    cd ${PRODUCT}
    if [ ! -z "$BRANCH" ]
    then
        git reset --hard
        git clean -xdf
        git checkout "$BRANCH"
    fi
    REVISION=$(git rev-parse --short HEAD)
    GITCOMMIT=$(git rev-parse HEAD 2>/dev/null)
    GITBRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
    echo "export VERSION=${VERSION}" > VERSION
    echo "export REVISION=${REVISION}" >> VERSION
    echo "export GITCOMMIT=${GITCOMMIT}" >> VERSION
    echo "export GITBRANCH=${GITBRANCH}" >> VERSION
    echo "export REVISION=${REVISION}" >> ${WORKDIR}/percona-server-mongodb-mongot.properties
    cd ${WORKDIR}
    rm -fr debian rpm ${PRODUCT}-${VERSION}

    mv ${PRODUCT} ${PRODUCT}-${VERSION}
    if [ -f ${CURDIR}/mongot.patch ]; then
        pushd ${PRODUCT}-${VERSION}
            cp ${CURDIR}/mongot.patch .
            sed -i "s:__VERSION__:${VERSION}:g" ./mongot.patch
            git apply ./mongot.patch || exit 1
            rm ./mongot.patch
        popd
    fi
    if [ ! -d ${PRODUCT}-${VERSION}/percona-packaging ]; then
        echo "ERROR: ${REPO} @ ${BRANCH} does not contain percona-packaging/."
        exit 1
    fi
    tar --owner=0 --group=0 --exclude=.git -czf ${PRODUCT}-${VERSION}.tar.gz ${PRODUCT}-${VERSION}
    echo "UPLOAD=UPLOAD/experimental/BUILDS/${PRODUCT}/${PRODUCT}-${VERSION}/${BRANCH}/${REVISION}/${BUILD_ID}" >> percona-server-mongodb-mongot.properties
    mkdir -p $WORKDIR/source_tarball
    mkdir -p $CURDIR/source_tarball
    cp ${PRODUCT}-${VERSION}.tar.gz $WORKDIR/source_tarball
    cp ${PRODUCT}-${VERSION}.tar.gz $CURDIR/source_tarball
    cd $CURDIR
    rm -rf ${PRODUCT}
    return
}

get_system(){
    if [ -f /etc/redhat-release ]; then
        RHEL=$(rpm --eval %rhel)
        ARCH=$(echo $(uname -m) | sed -e 's:i686:i386:g')
        OS_NAME="el$RHEL"
        OS="rpm"
    elif [ -f /etc/amazon-linux-release ]; then
        RHEL=$(rpm --eval %amzn)
        ARCH=$(echo $(uname -m) | sed -e 's:i686:i386:g')
        OS_NAME="el$RHEL"
        OS="rpm"
    else
        ARCH=$(uname -m)
        OS_NAME="$(lsb_release -sc)"
        OS="deb"
    fi
    return
}

install_deps() {
    if [ $INSTALL = 0 ]
    then
        echo "Dependencies will not be installed"
        return;
    fi
    if [ ! $( id -u ) -eq 0 ]
    then
        echo "It is not possible to instal dependencies. Please run as root"
        exit 1
    fi
    CURPLACE=$(pwd)

    if [ "x$OS" = "xrpm" ]; then
      #    curl is handled conditionally: OL9 / OL10 / AL2023 ship
      #    `curl-minimal` by default, and trying to install full `curl`
      #    rolls back the whole transaction. On OL8 only plain curl exists.
      #    Both variants provide /usr/bin/curl, which is all bazelisk needs.
      case "${RHEL}" in
          9|10|2023) yum -y install wget curl-minimal tar gzip bzip2 zip unzip which ;;
          *)         yum -y install wget curl         tar gzip bzip2 zip unzip which ;;
      esac
      yum -y install make git gcc gcc-c++
      yum -y install python3 python3-pip
      yum -y install rpm-build rpmdevtools systemd-rpm-macros

      if [ "x${RHEL}" = "x8" ]; then
          yum -y install java-21-openjdk-devel gcc-toolset-13
          source /opt/rh/gcc-toolset-13/enable
      fi
      if [ "x${RHEL}" = "x9" -o "x$RHEL" = "x10" ]; then
          yum -y install java-21-openjdk-devel gcc-toolset-13 openssl-devel
          source /opt/rh/gcc-toolset-13/enable
      fi
      if [ "x${RHEL}" = "x2023" ]; then
          yum -y install java-21-amazon-corretto-devel gcc14 gcc14-c++ openssl-devel
          export CC=gcc14-gcc CXX=gcc14-g++
      fi
      yum clean all
    else
      until apt-get -y update; do
        sleep 1
        echo "waiting"
      done
      DEBIAN_FRONTEND=noninteractive apt-get -y install lsb-release gpg wget ca-certificates
      export DEBIAN=$(lsb_release -sc)
      INSTALL_LIST="wget git devscripts debhelper debconf pkg-config make gcc g++ python3 python3-pip tar gzip bzip2 unzip zip ca-certificates fakeroot lintian"
      if [ x"${DEBIAN}" = xjammy ]; then
          INSTALL_LIST="${INSTALL_LIST} gcc-12 g++-12"
      fi
      if [ x"${DEBIAN}" = xnoble ]; then
          INSTALL_LIST="${INSTALL_LIST} python3-distutils-extra"
      fi
      until DEBIAN_FRONTEND=noninteractive apt-get -y install ${INSTALL_LIST}; do
        sleep 1
        echo "waiting"
      done
      if [ x"${DEBIAN}" = xjammy ]; then
          update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-12 100
          update-alternatives --install /usr/bin/g++ g++ /usr/bin/g++-12 100
          update-alternatives --install /usr/bin/cc cc /usr/bin/gcc-12 100
          update-alternatives --install /usr/bin/c++ c++ /usr/bin/g++-12 100
      fi
    fi
    return;
}

get_tar(){
    TARBALL=$1
    TARFILE=$(basename $(find $WORKDIR/$TARBALL -name 'percona-server-mongodb-mongot*.tar.gz' | sort | tail -n1))
    if [ -z $TARFILE ]
    then
        TARFILE=$(basename $(find $CURDIR/$TARBALL -name 'percona-server-mongodb-mongot*.tar.gz' | sort | tail -n1))
        if [ -z $TARFILE ]
        then
            echo "There is no $TARBALL for build"
            exit 1
        else
            cp $CURDIR/$TARBALL/$TARFILE $WORKDIR/$TARFILE
        fi
    else
        cp $WORKDIR/$TARBALL/$TARFILE $WORKDIR/$TARFILE
    fi
    return
}

variant_to_platform(){
    case "$1" in
        *aarch64*|*arm64*) echo "linux_aarch64" ;;
        *x64*|*x86_64*|*amd64*) echo "linux_x86_64" ;;
        *) echo "linux_x86_64" ;;
    esac
}

build_mongot(){
    if [ $MONGOT = 0 ]
    then
        echo "mongot will not be built"
        return;
    fi
    echo $PATH
    if [ "x$OS" = "xrpm" ]; then
      if [ "x${RHEL}" = "x7" ]; then
          source /opt/rh/devtoolset-11/enable
      fi
      if [ "x${RHEL}" = "x8" ]; then
          source /opt/rh/gcc-toolset-13/enable
      fi
      if [ "x${RHEL}" = "x9" -o "x$RHEL" = "x10" ]; then
          source /opt/rh/gcc-toolset-13/enable
      fi
      if [ "x${RHEL}" = "x2023" ]; then
          export CC=gcc14-gcc CXX=gcc14-g++
      fi
    fi
    get_tar "source_tarball"
    cd $WORKDIR
    rm -rf ${PRODUCT}-${VERSION}
    TARFILE=$(basename $(find . -name 'percona-server-mongodb-mongot*.tar.gz' | sort | tail -n1))
    tar xzf ${TARFILE}
    cd ${PRODUCT}-${VERSION}
    source VERSION

    PLATFORM=$(variant_to_platform "${VARIANT}")
    echo "Building mongot variant=${VARIANT} bazel-platform=${PLATFORM}"

    # Bazel needs a writable home for its caches; in CI containers HOME may be unset
    # or point to a non-writable location.
    export HOME="${HOME:-${WORKDIR}}"

    # Provide a stable cache location so consecutive runs benefit from it.
    BAZEL_OUTPUT_USER_ROOT="${WORKDIR}/.bazel-cache"
    mkdir -p "${BAZEL_OUTPUT_USER_ROOT}"

    BAZELISK="./scripts/tools/bazelisk/run.sh"

    # Run a basic compile first to surface errors early (mirrors `make build`),
    # then produce the deployable. We invoke bazelisk directly so we can pass
    # the version flag for stamping.
    ${BAZELISK} --output_user_root="${BAZEL_OUTPUT_USER_ROOT}" build //src/...
    ${BAZELISK} --output_user_root="${BAZEL_OUTPUT_USER_ROOT}" build \
        --platforms=//bazel/platforms:${PLATFORM} \
        --//bazel/config:version=${VERSION} \
        //deploy:mongot-community

    # The package_deploy_tar rule emits `mongot-community.tgz` under bazel-bin/deploy.
    BUILT_TGZ="bazel-bin/deploy/mongot-community.tgz"
    if [ ! -f "${BUILT_TGZ}" ]; then
        echo "Build did not produce expected artifact: ${BUILT_TGZ}"
        ls -la bazel-bin/deploy/ || true
        exit 1
    fi

    # The bundle is architecture-specific (it ships a bundled JDK and native
    # .so libraries selected via bazel `select(platform)`). Tag the output with
    # the bazel platform name so x86_64 and aarch64 bundles do not collide.
    #   1. Versioned + platform-tagged tarball under `tarball/` for direct release.
    #   2. `mongot-community-bundle-${PLATFORM}.tar.gz` under `bazel_tarball/`
    #      for downstream RPM/DEB packaging stages.
    OUT_NAME="${PRODUCT}-${VERSION}-${PLATFORM}.tar.gz"
    BUNDLE_NAME="mongot-community-bundle-${PLATFORM}.tar.gz"
    mkdir -p ${WORKDIR}/tarball ${CURDIR}/tarball
    mkdir -p ${WORKDIR}/bazel_tarball ${CURDIR}/bazel_tarball
    cp "${BUILT_TGZ}" "${WORKDIR}/tarball/${OUT_NAME}"
    cp "${BUILT_TGZ}" "${CURDIR}/tarball/${OUT_NAME}"
    cp "${BUILT_TGZ}" "${WORKDIR}/bazel_tarball/${BUNDLE_NAME}"
    cp "${BUILT_TGZ}" "${CURDIR}/bazel_tarball/${BUNDLE_NAME}"

    # Release the bazel server so its cache locks are dropped before exit.
    ${BAZELISK} --output_user_root="${BAZEL_OUTPUT_USER_ROOT}" shutdown || true
}

host_bazel_platform(){
    # Map `uname -m` to the bazel platform name used when stamping the bundle.
    case "$(uname -m)" in
        aarch64|arm64) echo "linux_aarch64" ;;
        x86_64|amd64)  echo "linux_x86_64" ;;
        *) echo "linux_x86_64" ;;
    esac
}

get_bundle(){
    # Fetch the mongot bundle that matches the current host architecture.
    # The bundle is per-arch because it ships a bundled JDK and native .so
    # libraries — running an aarch64 RPM stage with an x86_64 bundle would
    # produce a package whose binaries do not load.
    BUNDLE_PLATFORM=$(host_bazel_platform)
    BUNDLE_NAME="mongot-community-bundle-${BUNDLE_PLATFORM}.tar.gz"
    if [ -f "${WORKDIR}/bazel_tarball/${BUNDLE_NAME}" ]; then
        cp "${WORKDIR}/bazel_tarball/${BUNDLE_NAME}" "${WORKDIR}/mongot-community-bundle.tar.gz"
    elif [ -f "${CURDIR}/bazel_tarball/${BUNDLE_NAME}" ]; then
        cp "${CURDIR}/bazel_tarball/${BUNDLE_NAME}" "${WORKDIR}/mongot-community-bundle.tar.gz"
    else
        echo "No mongot bundle for ${BUNDLE_PLATFORM} found in bazel_tarball/."
        echo "Run --build_mongot=1 --build_variant=$(echo ${BUNDLE_PLATFORM} | sed 's/linux_x86_64/linux-x64/;s/linux_aarch64/linux-aarch64/') on a matching-arch host first."
        exit 1
    fi
}

build_rpm(){
    if [ $RPM = 0 ]; then
        echo "RPM will not be created"
        return
    fi
    if [ "x$OS" = "xdeb" ]; then
        echo "It is not possible to build rpm here"
        exit 1
    fi
    cd $WORKDIR
    get_tar "source_tarball"
    get_bundle
    rm -fr rpmbuild
    mkdir -vp rpmbuild/{SOURCES,SPECS,BUILD,SRPMS,RPMS}
    TARFILE=$(basename $(find . -maxdepth 1 -name 'percona-server-mongodb-mongot*.tar.gz' | sort | tail -n1))
    if ! tar -xzf ${TARFILE} --wildcards "*/percona-packaging" --strip=1; then
        echo "ERROR: source tarball does not contain percona-packaging/."
        exit 1
    fi
    tar -xzf ${TARFILE} --wildcards '*/VERSION'   --strip=1
    source VERSION
    sed -e "s:@@VERSION@@:${VERSION}:g" \
        -e "s:@@RELEASE@@:${RELEASE}:g" \
        percona-packaging/rpm/percona-server-mongodb-mongot.spec > rpmbuild/SPECS/percona-server-mongodb-mongot.spec
    cp ${TARFILE} rpmbuild/SOURCES/
    cp mongot-community-bundle.tar.gz rpmbuild/SOURCES/

    echo "RHEL=${RHEL}" >> ${WORKDIR}/percona-server-mongodb-mongot.properties
    echo "ARCH=${ARCH}" >> ${WORKDIR}/percona-server-mongodb-mongot.properties

    rpmbuild -bb \
        --define "_topdir ${WORKDIR}/rpmbuild" \
        --define "dist .${OS_NAME}" \
        --define "version ${VERSION}" \
        rpmbuild/SPECS/percona-server-mongodb-mongot.spec
    return_code=$?
    if [ $return_code != 0 ]; then
        exit $return_code
    fi
    mkdir -p ${WORKDIR}/rpm ${CURDIR}/rpm
    cp rpmbuild/RPMS/*/*.rpm ${WORKDIR}/rpm
    cp rpmbuild/RPMS/*/*.rpm ${CURDIR}/rpm
}

build_deb(){
    if [ $DEB = 0 ]; then
        echo "Binary deb package will not be created"
        return
    fi
    if [ "x$OS" = "xrpm" ]; then
        echo "It is not possible to build binary deb here"
        exit 1
    fi
    cd $WORKDIR
    get_tar "source_tarball"
    get_bundle
    rm -rf ${PRODUCT}-${VERSION}
    TARFILE=$(basename $(find . -maxdepth 1 -name 'percona-server-mongodb-mongot*.tar.gz' | sort | tail -n1))
    tar -xzf ${TARFILE}
    cd ${PRODUCT}-${VERSION}
    source VERSION

    # Place debian/ at the source root for dpkg-buildpackage; stash the prebuilt
    # bundle where debian/rules expects it.
    rm -rf debian
    cp -r percona-packaging/debian ./debian
    mkdir -p prebuilt
    cp ${WORKDIR}/mongot-community-bundle.tar.gz prebuilt/mongot-community-bundle.tar.gz

    export DEBIAN=$(lsb_release -sc)
    export ARCH=$(dpkg-architecture -qDEB_BUILD_ARCH)
    echo "DEBIAN=${DEBIAN}" >> ${WORKDIR}/percona-server-mongodb-mongot.properties
    echo "ARCH=${ARCH}"   >> ${WORKDIR}/percona-server-mongodb-mongot.properties

    dch -m -D "${DEBIAN}" --force-distribution -v "${VERSION}-${RELEASE}.${DEBIAN}" 'Build for distribution'
    dpkg-buildpackage -rfakeroot -us -uc -b

    cd ${WORKDIR}
    mkdir -p ${WORKDIR}/deb ${CURDIR}/deb
    cp ${WORKDIR}/*.deb ${WORKDIR}/deb
    cp ${WORKDIR}/*.deb ${CURDIR}/deb
}

#main

CURDIR=$(pwd)
VERSION_FILE=$CURDIR/percona-server-mongodb-mongot.properties
args=
WORKDIR=
SRPM=0
SDEB=0
RPM=0
DEB=0
SOURCE=0
TARBALL=0
MONGOT=0
OS_NAME=
ARCH=
OS=
INSTALL=0
RPM_RELEASE=1
DEB_RELEASE=1
VERSION="0.50.0"
RELEASE="1"
REVISION=0
BRANCH="main"
REPO="https://github.com/vorsel/percona-mongot.git"
PRODUCT=percona-server-mongodb-mongot
VARIANT="linux-x64"
parse_arguments PICK-ARGS-FROM-ARGV "$@"
PSM_BRANCH=${BRANCH}
if test -e "/proc/cpuinfo"
then
    NCPU="$(grep -c ^processor /proc/cpuinfo)"
else
    NCPU=4
fi

check_workdir
get_system
install_deps
get_sources
build_mongot
build_rpm
build_deb
