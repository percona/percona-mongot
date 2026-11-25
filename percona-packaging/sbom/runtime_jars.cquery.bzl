# cquery --output=starlark helper: prints the runtime classpath of a java target,
# i.e. exactly the jars that end up merged into its deploy jar.
def format(target):
    p = providers(target)
    for k in p.keys():
        if k.endswith("%JavaInfo") or k == "JavaInfo":
            return "\n".join([f.path for f in p[k].transitive_runtime_jars.to_list()])
    return ""
