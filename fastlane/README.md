fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android test

```sh
[bundle exec] fastlane android test
```

Run unit tests

### android ui_test

```sh
[bundle exec] fastlane android ui_test
```

Run UI tests (requires a connected device or emulator)

### android distribute_dev

```sh
[bundle exec] fastlane android distribute_dev
```

Submit a new Dev Build to Firebase App Distribution

### android distribute_qa

```sh
[bundle exec] fastlane android distribute_qa
```

Submit a new QA Build to Firebase App Distribution

### android distribute_prod

```sh
[bundle exec] fastlane android distribute_prod
```

Submit a new Prod Build to Firebase App Distribution

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
