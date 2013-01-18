# lein-bikeshed

A Leiningen plugin designed to tell you your code is bad, and that you
should feel bad.

## Usage

Add to your ~/.lein/profiles.clj:

```clojure
{:user {:plugins [[lein-bikeshed "0.1.0"]]}}
```

Just run `lein bikeshed` on your project:

```
∴ lein bikeshed
Checking for lines longer than 80 characters.
Badly formatted files:
/Users/hinmanm/src/clj/lein-bikeshed/test/bikeshed/core_test.clj:10:(def this-thing-is-over-eighty-characters-long "yep, it certainly is over eighty characters long")

Checking for files ending in blank lines.
Badly formatted files:
/Users/hinmanm/src/clj/lein-bikeshed/test/bikeshed/core_test.clj

Checking for redefined var roots in source directories.
with-redefs found in source directory:
/Users/hinmanm/src/clj/lein-bikeshed/src/bikeshed/core.clj:11: (with-redefs [+ -]
/Users/hinmanm/src/clj/lein-bikeshed/src/leiningen/bikeshed.clj:36: "xargs egrep -H -n '(\\(with-redefs)'")
```

## License

Copyright © 2012 Matthew Lee Hinman & Sonian

Distributed under the Eclipse Public License, the same as Clojure.
