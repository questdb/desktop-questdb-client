# quest

Quest is a desktop user interface to common 
`postgress wire protocol`-compatible databases such as:

- [**QuestDB**](https://github.com/questdb/questdb)
- [**Postgres**](https://github.com/postgres/postgres)

## Build commands

- <your system's gradle command> wrapper: regenerates the gradle scaffolding,
  *eg.* `gradle wrapper`, so that then you can use the subsequent commands.
- **build**: `./gradlew clean build`

## Run commands

- windows: `gradlew.bat run`
- mac/linux: `./gradlew run`

## Installation

After the **build** command completes, you will find a zip file in `build/distributions/`:

- `cd build/distributions`
- `unzip quest-*.zip`
- `cd quest-<version>`
- `bin/quest` (or `bin\quest.bat` in windows)
 