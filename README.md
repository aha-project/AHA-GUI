# AHA-GUI
The GUI portion of the AHA project which visualizes results from the AHA-Scraper developed by ESIC, Washington State University.

# Build Instructions
## Prerequisites
- Install Java 1.8
- Install apache ant
- Clone the repo

## Build
- cd to the directory with the build.xml in it
- run `ant`
- (if you'd like to clean after making changes and before rebuilding you can run `ant clean`

NOTE:
This project uses GraphStream. For now the dependencies are located within this repo for ease of securely building. Since the graphstream site does not have HTTPS enabled right now, this is the easiest way to ensure people can get the deps easily and safely. 


Beta screeenshot:
![Alt text](resources/AHA-GUI-Screenshot.png?raw=true "AHA-GUI Screenshot")