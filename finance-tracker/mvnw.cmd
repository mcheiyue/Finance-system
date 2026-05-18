@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN ("%~dp0\.mvn\wrapper\maven-wrapper.properties") DO @(
    IF "%%~A"=="wrapperUrl" SET "MW_DOWNLOADURL=%%~B"
    IF "%%~A"=="distributionUrl" SET "MW_DOWNLOADURL=%%~B"
)
@IF "%MW_DOWNLOADURL%"=="" (
    @SET __MVNW_ERROR__=Cannot read distributionUrl property in %~dp0\.mvn\wrapper\maven-wrapper.properties
    @GOTO :error
)
@IF "%MVNW_REPOURL%" NEQ "" (
    SET "MW_DOWNLOADURL=%MVNW_REPOURL%/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
)
@SET "MVNW_HASH_NAME=%MW_DOWNLOADURL:*/=%"
@SET "MVNW_HASH_NAME=%MVNW_HASH_NAME:/=_%"
@SET "MVNW_HASH_NAME=%MVNW_HASH_NAME::=%"
@SET "MVNW_HASH_NAME=%MVNW_HASH_NAME:.zip=%"
@SET "MVNW_HASH_NAME=%MVNW_HASH_NAME:.tar.gz=%"
@SET "MVNW_HASH_NAME=%MVNW_HASH_NAME:.tar.bz2=%"
@SET "MVN_CMD=mvn"
@SET "MVNW_DISTRIBUTION_URL_HASH=0"
@FOR %%A IN ("%MW_DOWNLOADURL%") DO @SET "MVNW_DISTRIBUTION_URL_HASH=%%~nxA"
@SET "MVNW_DISTRIBUTION_URL_HASH=%MVNW_DISTRIBUTION_URL_HASH:.zip=%"
@SET "MVNW_DISTRIBUTION_URL_HASH=%MVNW_DISTRIBUTION_URL_HASH:.tar.gz=%"
@SET "MVNW_DISTRIBUTION_URL_HASH=%MVNW_DISTRIBUTION_URL_HASH:.tar.bz2=%"

@SET "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9\%MVNW_DISTRIBUTION_URL_HASH%"
@IF EXIST "%MAVEN_HOME%\bin\%MVN_CMD%.cmd" (
    @SET "__MVNW_CMD__=%MAVEN_HOME%\bin\%MVN_CMD%.cmd"
    @GOTO :exec
)

@REM Download Maven distribution
@SET "TMP_DOWNLOAD_DIR=%TEMP%\mvnw_%RANDOM%%RANDOM%"
@MKDIR "%TMP_DOWNLOAD_DIR%" 2>NUL
@SET "DOWNLOAD_FILE=%TMP_DOWNLOAD_DIR%\%MVNW_DISTRIBUTION_URL_HASH%.zip"

@IF "%MVNW_VERBOSE%"=="true" (
    @ECHO [INFO] Downloading from: %MW_DOWNLOADURL%
    @ECHO [INFO] Downloading to: %DOWNLOAD_FILE%
)

@REM Try PowerShell first
@WHERE /Q powershell 2>NUL
@IF %ERRORLEVEL% EQU 0 (
    @IF "%MVNW_VERBOSE%"=="true" (
        powershell -Command "$ProgressPreference = 'Continue'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%MW_DOWNLOADURL%' -OutFile '%DOWNLOAD_FILE%'"
    ) ELSE (
        powershell -Command "$ProgressPreference = 'SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%MW_DOWNLOADURL%' -OutFile '%DOWNLOAD_FILE%'"
    )
) ELSE (
    @REM Fallback to BITSAdmin
    @WHERE /Q bitsadmin 2>NUL
    @IF %ERRORLEVEL% EQU 0 (
        bitsadmin /transfer mvnw-download /download /priority normal "%MW_DOWNLOADURL%" "%DOWNLOAD_FILE%"
    ) ELSE (
        @SET __MVNW_ERROR__=Cannot find PowerShell or BITSAdmin to download Maven distribution
        @GOTO :error
    )
)

@IF NOT EXIST "%DOWNLOAD_FILE%" (
    @SET __MVNW_ERROR__=Failed to download Maven distribution from %MW_DOWNLOADURL%
    @GOTO :error
)

@REM Extract the zip
@IF "%MVNW_VERBOSE%"=="true" (
    @ECHO [INFO] Extracting to: %TMP_DOWNLOAD_DIR%
)
powershell -Command "Expand-Archive -Path '%DOWNLOAD_FILE%' -DestinationPath '%TMP_DOWNLOAD_DIR%' -Force"
@IF %ERRORLEVEL% NEQ 0 (
    @SET __MVNW_ERROR__=Failed to extract Maven distribution
    @GOTO :error
)

@REM Move to MAVEN_HOME
@IF EXIST "%MAVEN_HOME%" @RMDIR /S /Q "%MAVEN_HOME%" 2>NUL
@MKDIR "%MAVEN_HOME%\.." 2>NUL
@MOVE /Y "%TMP_DOWNLOAD_DIR%\apache-maven-3.9.9" "%MAVEN_HOME%" >NUL 2>&1
@IF %ERRORLEVEL% NEQ 0 (
    @SET __MVNW_ERROR__=Failed to install Maven to %MAVEN_HOME%
    @GOTO :error
)

@REM Cleanup
@RMDIR /S /Q "%TMP_DOWNLOAD_DIR%" 2>NUL

@SET "__MVNW_CMD__=%MAVEN_HOME%\bin\%MVN_CMD%.cmd"

:exec
@IF "%__MVNW_CMD__%"=="" (
    @SET __MVNW_ERROR__=Cannot find Maven wrapper command
    @GOTO :error
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@SET __MVNW_PSMODULEP_SAVE=
@SET __MVNW_ARG0_NAME__=
@SET MVNW_REPOURL=
@SET MVNW_USERNAME=
@SET MVNW_PASSWORD=
@IF "%MVNW_VERBOSE%"=="true" (
    @ECHO [INFO] Using MAVEN_HOME: %MAVEN_HOME%
    @ECHO [INFO] Executing: %__MVNW_CMD__% %*
)
@"%__MVNW_CMD__%" %*
@IF %ERRORLEVEL% NEQ 0 GOTO error
@GOTO :end

:error
@SET __MVNW_ERROR_CODE__=%ERRORLEVEL%
@IF "%__MVNW_ERROR__%"=="" SET __MVNW_ERROR__=Maven wrapper execution failed with error code %__MVNW_ERROR_CODE__%
@ECHO [ERROR] %__MVNW_ERROR__% >&2
@SET __MVNW_ERROR__=
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@SET __MVNW_PSMODULEP_SAVE=
@SET __MVNW_CMD__=
@EXIT /B %__MVNW_ERROR_CODE__%

:end
@SET __MVNW_CMD__=
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@SET __MVNW_PSMODULEP_SAVE=
@IF "%MVNW_VERBOSE%"=="true" @ECHO [INFO] Finished
@EXIT /B 0
