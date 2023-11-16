# Openl Repository Migrator

> This project is created to help to migrate from one type of **DESIGN** repository to another one
> For example, the repository is stored at JCR which isn't supported by Openl Tablets since 5.23 and 
> it's needed to move projects from JCR to S3, Git or database storages.

## Getting Started

### Prerequisites
[Download the jar](https://github.com/openl-tablets/migration-tool/releases) and put the application.properties file in the same folder.
Example:<br/>
![Folder structure](https://github.com/openl-tablets/migration-tool/blob/master/site/folder.PNG)

#### Properties file
Properties file must be name application.properties and has the following structure:
``` 
# Source repository settings
repository.source.factory=org.openl.rules.repository.FactoryNeeded
repository.source.uri=uri_to_repo
repository.source.login=your_login
repository.source.password=your_password
repository.source.base.path=DESIGN/rules/

# Target repository settings
repository.target.factory=org.openl.rules.repository.FactoryNeeded
repository.target.uri=
repository.target.login=
repository.target.password=
repository.target.base.path=DESIGN/rules/
```
####
The properties file can be filled by copying the design repo settings from the properties file of the Webstudio.
It's needed to change the prefixes of the settings. More samples are in the [example folder](https://github.com/openl-tablets/migration-tool/tree/master/example/properties)

#### Target repository
If target repository was chosen as Git and this repository is empty (was created by webstudio/manually),
please, initialize the target branch. 

## Running the migrator
### Run it as usual jar file
After adding the properties file, it's need to launch the generated jar file.
```
java -jar openl-repository-migrator-1.0-SNAPSHOT.jar
```
or, if a JDBC driver for a database is required and another properties file:
```
java -classpath jdbc-driver.jar:openl-repository-migrator-1.0-SNAPSHOT.jar \
  org.openl.repository.migrator.App my-application.properties
```
This command will launch the migration process with setting which were added before.
During the migration there will be a log in console with detailed information.

#### Examples of property file
Examples for the different design repository settings are located in example/properties folder.

#### Requirements:
* JDK 11

### For developers
#### How to build
mvn clean package 

