
> ## payprotocol-appteam fork
>
> This is a fork of [**insideapp-fr/sonar-apple**](https://github.com/insideapp-fr/sonar-apple) (0.5.1),
> kept because upstream targets the SonarQube 9.9 LTA and has not been released against the
> SonarQube 26.x Community Build line, which is what the app team runs.
>
> ### What differs from upstream
>
> | Area | Upstream 0.5.1 | This fork |
> |---|---|---|
> | Syntax highlighting / CPD ranges | Token indexes (code points) compared against a UTF-16 line table, so a single emoji in a file shifted every following token — 3,223 `Unexpected error creating text range` warnings on one iOS project scan ([upstream #105](https://github.com/insideapp-fr/sonar-apple/issues/105)) | Line table is code point based and only the column is converted to UTF-16; end offsets are always `last character + 1`, trailing end-of-line trimmed |
> | `sonar.api.version` | 9.14.0.375 | 13.8.0.4399 — the API bundled with Community Build 26.9 |
> | `pluginApiMinVersion` | follows `sonar.api.version` | pinned to 9.14.0.375, so the plugin still loads on the 9.9 LTA |
> | Logging | `org.sonar.api.utils.log.Loggers` (deprecated) | SLF4J |
> | `Jre-Min-Version` | 11, while classes are compiled for 17 | 17 |
> | Plugin jar | 14.9 MB, with junit / byte-buddy / guava / xerces bundled | 4.4 MB, unused dependencies dropped |
> | SwiftLint rules | 233 (SwiftLint ~0.5x) | 262 (SwiftLint 0.65.1) |
> | CI | macOS + upstream SonarCloud secrets | `build.yml`: ubuntu-latest, `mvn -B verify`, jar artifact |
>
> Everything else — sensors, properties, rule keys, report formats — is unchanged, so a
> `sonar-project.properties` written for upstream keeps working. Contributor documentation is in
> [`DEVELOP.md`](DEVELOP.md), the agent-facing build/verify notes in [`CLAUDE.md`](CLAUDE.md).

![CI](https://github.com/insideapp-fr/sonar-flutter/workflows/CI/badge.svg)

[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=insideapp-oss_sonar-apple&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=insideapp-oss_sonar-apple)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=insideapp-oss_sonar-apple&metric=coverage)](https://sonarcloud.io/summary/new_code?id=insideapp-oss_sonar-apple)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=insideapp-oss_sonar-apple&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=insideapp-oss_sonar-apple)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=insideapp-oss_sonar-apple&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=insideapp-oss_sonar-apple)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=insideapp-oss_sonar-apple&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=insideapp-oss_sonar-apple)


# SonarQube plugin for Swift / Objective-C

A plugin to enable analysis of Swift and Objective-C code quality and security.

Let us know if you want to get involved.

## Features

The plugin is designed to support Swift 5 syntax.

| Feature             | Tool(s)           | Availability       |
|---------------------|-------------------|--------------------|
| Tests               | Xcode             | Swift, Objective-C |
| Coverage            | Xcode             | Swift, Objective-C |
| Complexity          | SonarQube         | Swift, Objective-C |
| Dead code           | Periphery         | Swift              |
| Size                | SonarQube         | Swift, Objective-C |
| Syntax highlighting | SonarQube         | Swift, Objective-C |
| Issues              | SwiftLint, OCLint | Swift, Objective-C |
| Security            | mobsfscan         | Swift, Objective-C |

## Installation

### Server-side

SonarQube 9.9+ is required. This fork is verified on SonarQube Community Build 26.9.0.129388.

- Download the plugin binary into the ``$SONARQUBE_HOME/extensions/plugins`` directory.
- Restart the server.
- Activate the rules in your Quality Profiles.

### Client-side

Xcode 13+ and SonarScanner are required.
The following tools are optional:

- [SwiftLint](https://github.com/realm/SwiftLint)
- [OCLint](https://oclint.org/)
- [mobsfscan](https://github.com/MobSF/mobsfscan)
- [Periphery](https://github.com/peripheryapp/periphery)

#### Sonar configuration

Create a ``sonar-project.properties`` file at the root with this content:

```properties
# Project identification
sonar.projectKey=ios_app
sonar.projectName=iOS App
sonar.projectVersion=1.0
	
# Source code location.
# Path is relative to the sonar-project.properties file. Defaults to .
# Use commas to specify more than one folder.
sonar.sources=iOSApp
# Tests source code location.
# Path is relative to the sonar-project.properties file. Defaults to empty.
# Use commas to specify more than one folder.
sonar.tests=iOSAppTests

## Coverage & Tests ##

# Path to the Xcode result bundle file. 
# The path is relative to the project base directory.
# Defaults to build/result.xcresult
#sonar.apple.resultBundlePath=custom/path/to/file.xcresult

## Periphery ##

# Index Store folder path.
# This matches the parameter "-derivedDataPath" in xcodebuild (see below).
# Warning: starting Xcode 14 the folder "Index" is renamed "Index.noindex".
sonar.apple.periphery.indexStorePath=derivedData/Index/DataStore

## OCLint ##

# Path to the JSON Compilation Database folder
# The path is relative to the project base directory.
# Defaults to build/json_compilation_database
# sonar.apple.jsonCompilationDatabasePath=custom/path/to/folder

## Misc ##

# Encoding of the source code. Default is default system encoding.
sonar.sourceEncoding=UTF-8
```

For a complete list of available options, please refer to the [SonarQube documentation](https://docs.sonarqube.org/latest/analysis/analysis-parameters/).

#### Run analysis

Use the following commands from the root folder to start an analysis:

```bash
# Don't forget to add -workspace to the build command if your project is part of a workspace
# Don't forget to activate 'Gather coverage' option in the app scheme or add '-enableCodeCoverage YES' to the following command

# Run tests 
xcrun xcodebuild \
  -project MyApp.xcodeproj \
  -scheme MyApp \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 11 Pro' \
  -derivedDataPath ./derivedData \
  -resultBundlePath build/result.xcresult \
  OTHER_CFLAGS="\$(inherited) -gen-cdb-fragment-path build/compilation_database" \
  -quiet \
  clean test

# Run the analysis and publish to the SonarQube server
# Don't forget to specify `sonar.host.url` and `sonar.token` in `sonar-project.properties` or supply it to the following command.
sonar-scanner
```

### Advanced configuration

#### Periphery

The plugin assumes the Periphery configuration is properly settled for your project, in the [Periphery configuration file](https://github.com/peripheryapp/periphery#configuration).
The required information are the project, the schemes and the targets. You also need to provide the workspace, if you have one.
```yaml
workspace: path/to/workspace.xcworkspace # optional
project: path/to/project.xcodeproj
schemes:
  - MyScheme
targets:
  - MyTarget
```

#### OCLint

On macOS, the system will block usage of OCLint. In order to get rid of the manual verification of each of them, use the following commands:

```bash
sudo xattr -dr com.apple.quarantine /usr/local/lib/oclint/rules/lib*
sudo xattr -dr com.apple.quarantine /usr/local/lib/oclint/reporters/lib*
```

#### Sonar Scanner

If you have trouble running the Sonar Scanner, you can run it in verbose mode, to get more logs and information.
You can either:
- add `sonar.verbose=true` to your `sonar-project.properties`
- add the option `X` to the command, like so: `sonar-scanner -X ...`

## Contributing

Any help is welcome, and PRs will be greatly appreciated!

Have a look at the [developer guide](https://github.com/insideapp-fr/sonar-apple/blob/main/DEVELOP.md) to get started.

## License

This plugin is released under the GNU LGPL v3 license. See the [LICENSE](https://github.com/insideapp-fr/sonar-apple/blob/main/LICENSE.md) file for more information.
