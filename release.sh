rm -rf release/dependency/
mkdir -p release/dependency/
mvn clean
mvn compile
mvn dependency:copy-dependencies
cp -rf target/dependency/* release/dependency/

git rm -rf  release/classes
cp -rf target/classes release

# auto push to git
#git add .
#log="auto-deploy"
#if [ "$1" != "" ];then
#    log=$1
#fi
#git commit -m'auto-deploy'
#git push origin master
