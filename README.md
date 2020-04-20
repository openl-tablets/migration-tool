# Openl Repository Migrator

> This project is created to help to migrate from one type of **DESIGN** repository to another one
> For example, the repository is stored at JCR which isn't supported by Openl Tablets since 5.23 and 
> it's needed to move projects from JCR to S3, Git or database storages.

## Getting Started

### Prerequisites
Download the jar and put the application.properties file in the same folder.
Example:
![Folder structure](https://github.com/openl-tablets/migration-tool/site/folder.png)

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
repository.target.factory=org.openl.rules.repository.db.JdbcDBRepositoryFactory
repository.target.uri=
repository.target.login=
repository.target.password=
repository.target.base.path=DESIGN/rules/
```
The properties file can be filled by copying the design repo settings from the properties file of the Webstudio.
It's needed to change the prefixes of the settings. More samples are in the example folder.
#### Jackrabbit Factory
If you want to copy design repository from Jackrabbit, please setup the following package name
```
org.openl.repository.migrator.jackrabbit.
```

## Running the migrator
### Run it as usual jar file
After adding the properties file, it's need to launch the generated jar file.
```
java -jar openl-repository-migrator-1.0-SNAPSHOT.jar
```
This command will launch the migration process with setting which were added before.
During the migration there will be a log in console with detailed information.

#### Examples of property file
Examples for the different design repository settings are located in example/properties folder.

#### Requirements:
* JDK 8

### For developers
#### How to build
mvn clean package 

