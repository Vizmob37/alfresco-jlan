# ! /bin/bash

echo "Alfresco JLAN Server starting, enter 'x' to shutdown server, 'r' to restart server ..."
java -Djava.util.logging.config.file=./logging.properties -Djava.library.path=./jni -cp ./target/mock-jlan-1.0.2.jar:./target/dependency/*:./libs/cryptix-jce-provider.jar org.alfresco.jlan.app.JLANServer jlanConfig.xml
