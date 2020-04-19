# Openl Repository Migrator

> This project is created to help to migrate from one type of **DESIGN** repository to another one
> For example, the repository is stored at JCR which isn't supported by Openl Tablets since 5.23 and 
> it's needed to move projects from JCR to S3, Git or database storages.

## Getting Started

### Prerequisites
Download the project, build it locally, and add the application.properties file to the folder with generated jar.

#### How to build
mvn clean package 
#### Properties file
Properties file must be name application.properties and  has the following structure:
``` 
# Source repository settings
repository.source.factory=org.openl.rules.repository.FactoryNeeded
repository.source.uri=uri_to_repo
repository.source.login=your_login
repository.source.password=your_password

# Target repository settings
repository.target.factory=org.openl.rules.repository.db.JdbcDBRepositoryFactory
repository.source.uri=
repository.source.login=
repository.source.password=
```
The properties file can be filled by copying the design repo settings from the properties file of the Webstudio.
It's needed to change the prefixes of the settings.
#### Jackrabbit Factory
If you want to copy design repository from Jackrabbit, please setup the following package name
```
org.openl.repository.migrator.jackrabbit.
```
#### Examples of property file
Examples for the different design repository settings are located in example/properties folder.


## Running the migrator
### Run it as usual jar file
```
java -jar openl-repository-migrator-1.0-SNAPSHOT.jar
```


#### Requirements:
* JDK 8

