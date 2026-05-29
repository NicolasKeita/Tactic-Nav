@echo off
rem ----------------------------------------------------------------------------
rem Maven Wrapper
rem ----------------------------------------------------------------------------

set "BASEDIR=%~dp0"
set "CLASSPATH=%BASEDIR%.mvn\wrapper\maven-wrapper.jar"
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java"
)
"%JAVA_EXE%" -cp "%CLASSPATH%" org.apache.maven.wrapper.MavenWrapperMain %*
