%undefine _missing_build_ids_terminate_build
%global debug_package %{nil}
%global __os_install_post %{nil}
%global __jar_repack 0
%{!?with_systemd:%global systemd 0}
%{?el7:          %global systemd 1}
%{?el8:          %global systemd 1}
%{?el9:          %global systemd 1}
%{?el10:         %global systemd 1}
%{?amzn2023:     %global systemd 1}

Name:    percona-server-mongodb-mongot
Version: @@VERSION@@
Release: @@RELEASE@@%{?dist}
Summary: Percona Search for MongoDB (mongot)

Group:   Applications/Databases
License: SSPL-1.0
URL:     https://github.com/percona/percona-mongot
Source0: percona-server-mongodb-mongot-%{version}.tar.gz
Source1: mongot-community-bundle.tar.gz

# The bundle ships a prebuilt JDK and native .so libraries; skip auto-discovery.
AutoReqProv: no

Requires(pre):  /usr/sbin/useradd, /usr/bin/getent
Requires(postun): /usr/sbin/userdel
%if 0%{?systemd}
BuildRequires:  systemd
Requires(post):   systemd
Requires(preun):  systemd
Requires(postun): systemd
%endif

%description
Percona Search for MongoDB is the Percona-supported distribution of
MongoDB Search (mongot). It provides Full Text and Vector Search
capabilities for MongoDB and is intended to run alongside Percona Server
for MongoDB to serve search queries and manage search indexes.

%prep
%setup -q -n percona-server-mongodb-mongot-%{version}

%build
# The mongot bundle is built externally with Bazel (see builder.sh). Nothing
# to compile here; %install just lays the prebuilt tarball into place.

%install
rm -rf $RPM_BUILD_ROOT
install -m 0755 -d $RPM_BUILD_ROOT%{_libdir}/percona-server-mongodb-mongot
install -m 0755 -d $RPM_BUILD_ROOT%{_bindir}
install -m 0755 -d $RPM_BUILD_ROOT%{_sysconfdir}/mongot
install -m 0755 -d $RPM_BUILD_ROOT%{_sysconfdir}/sysconfig
install -m 0755 -d $RPM_BUILD_ROOT%{_sysconfdir}/logrotate.d
install -m 0750 -d $RPM_BUILD_ROOT%{_sharedstatedir}/mongot
install -m 0750 -d $RPM_BUILD_ROOT%{_localstatedir}/log/mongot

# Lay out the prebuilt bundle.
tar -xzf %{SOURCE1} -C $RPM_BUILD_ROOT%{_libdir}/percona-server-mongodb-mongot --strip-components=1

# Thin launcher on PATH.
install -m 0755 percona-packaging/conf/mongot-wrapper.sh $RPM_BUILD_ROOT%{_bindir}/mongot

# Config + env + logrotate.
install -m 0640 percona-packaging/conf/mongot.yml      $RPM_BUILD_ROOT%{_sysconfdir}/mongot/mongot.yml
install -m 0640 percona-packaging/conf/mongot.env      $RPM_BUILD_ROOT%{_sysconfdir}/sysconfig/mongot
install -m 0644 percona-packaging/conf/mongot.logrotate $RPM_BUILD_ROOT%{_sysconfdir}/logrotate.d/mongot

%if 0%{?systemd}
install -m 0755 -d $RPM_BUILD_ROOT%{_unitdir}
install -m 0644 percona-packaging/conf/mongot.service $RPM_BUILD_ROOT%{_unitdir}/mongot.service
%endif

%pre
/usr/bin/getent group mongod  >/dev/null || /usr/sbin/groupadd -r mongod
/usr/bin/getent passwd mongod >/dev/null || \
    /usr/sbin/useradd -M -r -g mongod -d /var/lib/mongo -s /bin/false -c mongod mongod

%post
%if 0%{?systemd}
%systemd_post mongot.service
if [ $1 -eq 1 ]; then
    /usr/bin/systemctl enable mongot.service >/dev/null 2>&1 || :
fi
%endif
chown -R mongod:mongod %{_sharedstatedir}/mongot
chown -R mongod:mongod %{_localstatedir}/log/mongot
chown -R root:mongod   %{_sysconfdir}/mongot
chmod 0750             %{_sysconfdir}/mongot

%preun
%if 0%{?systemd}
%systemd_preun mongot.service
%endif

%postun
%if 0%{?systemd}
%systemd_postun_with_restart mongot.service
%endif

%files
%defattr(-,root,root,-)
%{_libdir}/percona-server-mongodb-mongot
%{_bindir}/mongot
%dir %attr(0750, root, mongod) %{_sysconfdir}/mongot
%config(noreplace) %attr(0640, root, mongod) %{_sysconfdir}/mongot/mongot.yml
%config(noreplace) %attr(0640, root, root)   %{_sysconfdir}/sysconfig/mongot
%{_sysconfdir}/logrotate.d/mongot
%if 0%{?systemd}
%{_unitdir}/mongot.service
%endif
%dir %attr(0750, mongod, mongod) %{_sharedstatedir}/mongot
%dir %attr(0750, mongod, mongod) %{_localstatedir}/log/mongot

%changelog
* Sun May 10 2026 Oleksandr Miroshnychenko <alex.miroshnychenko@percona.com>
- Initial Percona packaging of MongoDB Search (mongot).
