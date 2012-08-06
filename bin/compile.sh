#!/bin/bash
#加载环境变量
source /etc/profile

W_EXIT_STATUS=65
number_of_expected_args=1

# 执行该脚本时 有可能是符号链接 我们要找到它的实际位置
SCRIPT="$0"
while [ -h "$SCRIPT" ] ; do
  ls=`ls -ld "$SCRIPT"`
  # Drop everything prior to ->
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    SCRIPT="$link"
  else
    SCRIPT=`dirname "$SCRIPT"`/"$link"
  fi
done

S_HOME=`dirname "$SCRIPT"`/..
S_HOME=`cd $S_HOME; pwd`

cd $S_HOME

echo "step into $S_HOME"

function s_mkdir
{
  directory=${1-:"s_temp"}
  if [ ! -d $directory ];then
   mkdir $directory
  fi
}


classpath=.
for jarfile in `ls lib`
do
   classpath=$classpath:lib/$jarfile
done

for source in `find src -type f -iname "*.java"`
do
   sourcefiles=$sourcefiles" "$source
done

echo "create build direcotry if it's not exits"
s_mkdir "$S_HOME/build"

copy_file_list="bin lib config"
new_file_list="gateway data logs"

for file in $copy_file_list
do
    if [ ! -d build/$file ];then
    echo "copy $file to $S_HOME/build"
    cp -r $file $S_HOME/build
    fi
done

for file in $new_file_list
do
    if [ ! -d build/$file ];then
        echo "create file:$S_HOME/build/$file"
        mkdir $S_HOME/build/$file
    fi
done

javac -cp $classpath -d "$S_HOME/build" -encoding UTF-8 $sourcefiles

echo "copy build/bin/run.sh to build and chmod u+x"
cp -f  "build/bin/run.sh" "build"
chmod u+x  "build/run.sh"
