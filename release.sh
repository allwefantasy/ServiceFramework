#mvn clean deploy -DskipTests -Prelease-sign-artifacts -Pscala-2.11
#https://oss.sonatype.org/#stagingRepositories
mvn versions:set -DnewVersion=2.0.6
mvn clean deploy -DskipTests -Prelease-sign-artifacts -Pscala-2.11


./dev/change-scala-version.sh 2.12
mvn clean deploy -DskipTests -Prelease-sign-artifacts -Pscala-2.12
git co .