mvn clean deploy -DskipTests -Prelease-sign-artifacts -Pscala-2.12
#https://oss.sonatype.org/#stagingRepositories
#mvn versions:set -DnewVersion={version}