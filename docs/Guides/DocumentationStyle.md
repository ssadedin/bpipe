# Filenames

All documentation should be in Markdown, and with an `.md` extension

The documentation menu is built from the filename and directory, and converted
to mkdocs menu syntax. Capital letters indicate a space in the menu heading.
For exmaple::

```
docs/Documention/DocumentationStyle.md
```

is extracted to a filename, submenu, title format such as

```
-['DocumentationStyle.md','Documentation','Documentation Style']
```

# Testing / Building Documentation

The documentation is built using mkdocs. After installing mkdocs, a local 
server can be started to view the documentation using the following command
in the Bpipe main directory:

```
mkdocs serve 
```

