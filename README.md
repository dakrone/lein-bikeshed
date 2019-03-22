# lein-bikeshed

A Leiningen plugin designed to tell you your code is bad, and that you
should feel bad.

## Usage

Add to your ~/.lein/profiles.clj:

```clojure
{:user {:plugins [[lein-bikeshed "0.5.2"]]}}
```

Just run `lein bikeshed` on your project:

```
∴ lein bikeshed

Checking for lines longer than 80 characters.
Badly formatted files:
/home/hinmanm/src/clj/lein-bikeshed/test/bikeshed/core_test.clj:10:(def this-thing-is-over-eighty-characters-long "yep, it certainly is over eighty characters long")

Checking for lines with trailing whitespace.
Badly formatted files:
/home/hinmanm/src/clj/lein-bikeshed/test/bikeshed/core_test.clj:5:(deftest a-test /home/hinmanm/src/clj/lein-bikeshed/test/bikeshed/core_test.clj:6: (testing "FIXME, I fail, and I have trailing whitespace!"

Checking for files ending in blank lines.
Badly formatted files:
/home/hinmanm/src/clj/lein-bikeshed/src/bikeshed/core.clj
/home/hinmanm/src/clj/lein-bikeshed/test/bikeshed/core_test.clj

Checking for redefined var roots in source directories.
with-redefs found in source directory:
/home/hinmanm/src/clj/lein-bikeshed/src/bikeshed/core.clj:17: (with-redefs [+ -]
/home/hinmanm/src/clj/lein-bikeshed/src/bikeshed/core.clj:92: "xargs egrep -H -n '(\\(with-redefs)'")

Checking whether you keep up with your docstrings.
1/2 [50.00%] namespaces have docstrings.
10/12 [83.33%] functions have docstrings.
Use -v to list functions without docstrings")")))

Checking for arguments colliding with clojure.core functions.
#'bikeshed.core/colliding-arguments: 'map', 'first' are colliding with core functions

The following checks failed:
 * long-lines
 * trailing-whitespace
 * trailing-blank-lines
 * var-redefs
 * name-collisions

```

## Options

| Switches                    | Default | Desc                        |
| --------------------------- | ------- | --------------------------- |
| -H, --no-help-me, --help-me | false   | Show help                   |
| -v, --no-verbose, --verbose | false   | Display missing doc strings |
| -m, --max-line-length       | 80      | Max line length             |
| -l, --long-lines            | true    | Check for lines with length > max line length |
| -w, --trailing-whitespace   | true    | Check for trailing whitespace |
| -b, --trailing-blank-lines  | true    | Check for trailing blank lines |
| -r, --var-redefs            | true    | Check for redefined root vars |
| -d, --docstrings            | true    | Generate a report of docstring coverage |
| -n, --name-collisions       | true    | Check for function arg names that collide with clojure.core |
| -x, --exclude-profiles      |         | Comma-separated profile exclusion |

You can also add the `:bikeshed` option map directly to your `project.clj`:

```clj
(defproject my-thing "1.0.0"
  :description "A thing"
  ;; Override the default max-line-length
  :bikeshed {:max-line-length 60
             :var-redefs false
             :name-collisions false}
  :dependencies [[clj-http "3.3.0"]])
```

## License

Copyright © 2012 Matthew Lee Hinman & Sonian

Distributed under the Eclipse Public License, the same as Clojure.
