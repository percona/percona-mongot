cc_binary(
    name = "mongot_vecsim",
    srcs = ["mongot_vecsim.c"],
    copts = [
        "-O3",
        "-DNDEBUG",
    ] + select({
        "//bazel/platforms:is_linux_aarch64": ["-march=armv8.2-a+dotprod+sve"],
        "//bazel/platforms:is_macos_aarch64": ["-march=armv8.2-a+dotprod"],
        "//conditions:default": [],
    }),
    linkshared = True,
    linkstatic = False,
    visibility = ["//visibility:public"],
)
