# Desktop QuestDB Client

This is a desktop user interface to common `postgress wire protocol`-compatible databases such as:

- [**QuestDB**](https://github.com/questdb/questdb/)
- [**Postgres**](https://github.com/postgres/postgres/)

## Run commands

- windows: `gradlew.bat run`
- mac/linux: `./gradlew run`

## Build commands

- <your system's gradle command> wrapper: regenerates the gradle scaffolding,
  *eg.* `gradle wrapper`, so that then you can use the subsequent commands.
- **build**: `./gradlew clean build`

## Installation

After the **build** command completes, you will find a zip file in `build/distributions/`:

- `cd build/distributions`
- `unzip desktop-questdb-client-*.zip`
- `cd desktop-questdb-client-<version>`
- `bin/start-client` (or `bin\start-client.bat` in windows)
