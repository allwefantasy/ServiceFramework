mvn clean deploy -DskipTests -Prelease-sign-artifacts
#https://oss.sonatype.org/#stagingRepositories
#mvn versions:set -DnewVersion={version}