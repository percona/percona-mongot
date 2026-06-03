# Contributing guide

Welcome to Percona Search for MongoDB!

1. [Prerequisites](#prerequisites)
2. [Submitting a pull request](#submitting-a-pull-request)
3. [Building from source](#building-from-source)
4. [Contributing to documentation](#contributing-to-documentation)

We're glad that you would like to become a Percona community member and participate in keeping open source open.

Percona Search for MongoDB is Percona's distribution of `mongot`, the search service that provides Full Text Search and Vector Search capabilities for Percona Server for MongoDB.

You can contribute in one of the following ways:

1. Reach us on our [Forums](https://forums.percona.com/).
2. [Submit a bug report or a feature request](https://jira.percona.com/projects/PSMDB)
3. Submit a pull request (PR) with the code patch
4. Contribute to documentation

## Prerequisites

Before submitting code contributions, we ask you to complete the following prerequisites.

### 1. Sign the CLA

Before you can contribute, we kindly ask you to sign our [Contributor License Agreement](https://cla-assistant.percona.com/percona/percona-mongot) (CLA). You can do this in one click using your GitHub account.

**Note**: You can sign it later, when submitting your first pull request. The CLA assistant validates the PR and asks you to sign the CLA to proceed.

### 2. Code of Conduct

Please make sure to read and agree to our [Code of Conduct](CODE_OF_CONDUCT.md).

## Submitting a pull request

All bug reports, enhancements and feature requests are tracked in the [Jira issue tracker](https://jira.percona.com/projects/PSMDB). Though not mandatory, we encourage you to first check for a bug report among Jira issues and in the PR list: perhaps the bug has already been addressed.

For feature requests and enhancements, we do ask you to create a Jira issue, describe your idea and discuss the design with us. This way we align your ideas with our vision for the product development.

If the bug hasn't been reported / addressed, or we've agreed on the enhancement implementation with you, do the following:

1. [Fork](https://docs.github.com/en/github/getting-started-with-github/fork-a-repo) this repository.
2. Clone this repository on your machine.
3. Create a separate branch for your changes. If you work on a Jira issue, please include the issue number in the branch name so it reads as `PSMDB-XXX-my_branch`. This makes it easier to track your contribution.
4. Make your changes. Please follow the existing code style and conventions in the repository to improve code readability.
5. Test your changes locally. See the [Building from source](#building-from-source) section for more information.
6. Commit the changes. Add the Jira issue number at the beginning of your message subject so that it reads as `PSMDB-XXX - My subject`. The [commit message guidelines](https://gist.github.com/robertpainsi/b632364184e70900af4ab688decf6f53) will help you with writing great commit messages.
7. Open a PR to Percona.
8. Our team will review your code and if everything is correct, will merge it. Otherwise, we will contact you for additional information or with a request to make changes.

## Building from source

To build `mongot` from source, see [docs/building.md](docs/building.md). In short, `make build` compiles and tests the project.

## Contributing to documentation

We welcome contributions to our documentation. Documentation source files for this repository live in the [`docs/`](docs/) directory.

## After your pull request is merged

Once your pull request is merged, you are an official Percona Community Contributor. Welcome to the community!
