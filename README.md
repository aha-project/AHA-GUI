# AHA-GUI
This repository contains the GUI portion of the AHA (AttackSurface Host Analyzer) project which visualizes results from [AHA-Scraper-Lin](https://github.com/aha-project/AHA-Scraper-Lin) and [AHA-Scraper-Win](https://github.com/aha-project/AHA-Scraper-Win).

AHA-GUI allows you to interactively inspect the graph, view connections, and gauge relative vulnerability of the components. 

Developed by ESIC, Washington State University.

# User Instructions
[Click here for user walkthrough / documentation](https://aha-project.github.io/)

# Build Instructions
Note: if you're just looking to run AHA-GUI builds are periodically posted under [the GitHub Repo's releases tab](https://github.com/aha-project/AHA-GUI/releases).

## Build Prerequisites
- Install Java 1.8 (required to build and run)
- Install apache ant (only required to build)
- Clone the repo

## Build
- `cd` to the directory with the `build.xml` in it
- run `ant`
- the `build` directory will contain the resulting built project
- (if you'd like to clean after making changes and before rebuilding you can run `ant clean`)

# NOTE:
This project uses GraphStream. For now the dependencies are located within this repo for ease of securely building. Since the graphstream site does not have HTTPS enabled right now, this is the easiest way to ensure people can get the deps easily and safely. 


# Screeenshots:
Beta Screenshot:
![Alt text](resources/AHA-GUI-Screenshot.png?raw=true "AHA-GUI Screenshot")
