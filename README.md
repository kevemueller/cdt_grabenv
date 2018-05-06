# cdt_grabenv
Tiny utility to apply massive changes to the environment performed by a Visual Studio vcvars script on an Eclipse CDT project environment


## Motivation

Often a compilation environment is set-up by a parameterizable script. 
Under Windows, Visual Studio provides the vcvarsall.bat script that performs this setup.
Eclipse CDT allows multiple build configurations and has the feature to define custom environment variables per configuration.
Ideally there would be a feature to import (from file or from a script) the environment into the CDT configuration. Unfortunately such a feature was not yet implemented.


## Solution

This small _hack_ modifies an existing Eclipse CDT projet definition by applying the environment that was captured by running vcvars.bat. 

## Usage

1.  Create a CDT project and create configurations, e.g. x64_msvc_Release, x86_msvc_Debug, x64_linux_Release
2.  Close the CDT projet
3.  Run cdt_grabenv on the project with appropriate parameters to modify the environment settings.
4.  Open the CDT project and enjoy the different builds.

# Command line reference

	CDTGrabEnv -- Apply environment to Eclipse CDT configurations
	Usage:
	java -jar CDTGrabEnv.jar <<options>>
	Option                  Description                                            
	------                  -----------                                            
	-?, -h, --help                                                                 
	--arch <String>         Architecture to pass to vcvarsall. E.g. x64, x86, x64_ 
                          	[x86|arm|arm64], x86_[x64|arm|arm64] (default: x64)  
	-n, --dry-run                                                                  
	--namePattern <String>  CDT configuration name filter (regular expression)     
                          	(default: .+)                                        
	--projectRoot <File>    Eclipse CDT project root directory (default: .)        
	-v, --verbose                                                                  
	--vsRoot <File>         Visual Studio root directory (default: C:\Program Files
                          (x86)\Microsoft Visual Studio\2017\Community)        
 
## Example

Assuming you have created the [x86|x64]_msvc_[Release|Debug] configurations (4 configurations) and are on an x64 host, you would run

`java -jar CDTGrabEnv.jar -v --namePattern x64_msvc_.*`

`java -jar CDTGrabEnv.jar -v --arch x64_x86 --namePattern x86_msvc_.*`

to populate the environments with the appropriate values.
Now you can build these different environments along each other without leaving Eclipse.
 
