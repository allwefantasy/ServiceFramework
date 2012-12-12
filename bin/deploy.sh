#!/bin/bash
#加载环境变量
source /etc/profile

#一些有用的函数
function s_mkdir
{
  local directory=${1-"s_temp"}
  if [ ! -d $directory ];then
   mkdir -p $directory
   echo "mkdir => [$direcotry]"
  else
   echo "already exsits => [$direcotry]"
  fi
}

valid_status()
{
	if [ $1 -ne 0 ];then
		echo "$2 error !!! exit....."
		exit $W_EXIT_STATUS
	fi
}

#默认我们设置为开发环境 p env=p 为生产环境  env=d 为开发环境
env=${3-"d"}
build_dir=${2-"build"}




#虚拟机参数
min_heap_size="1300m"
max_heap_size="1300m"

#生产环境设置
#if [ $env == "p" ];then
#	min_heap_size="3300m"
#	max_heap_size="3300m"x
#fi

#异常退出code
W_EXIT_STATUS=65

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

#找到项目根目录
S_HOME=`dirname "$SCRIPT"`/..
S_HOME=`cd $S_HOME; pwd`

DEPLOY_ROOT="$S_HOME/deploy"
DEPLOY_TO="$S_HOME/deploy/$build_dir"
DEPLOY_CURRENT="$S_HOME/current"

s_mkdir $DEPLOY_ROOT
s_mkdir $DEPLOY_TO



#进入根目录
cd $S_HOME
echo "ROOT Directory => $S_HOME"


#获取jar列表
classpath=.
for jarfile in `ls lib`
do
   classpath=$classpath:lib/$jarfile
done


#获取所有待编译的java文件
for source in `find src -type f -iname "*.java"`
do
   sourcefiles=$sourcefiles" "$source
done



start()
{
  echo "staring system [`cd $DEPLOY_CURRENT;pwd -P`]....."
  cd $DEPLOY_CURRENT
  nohup java -Xms$min_heap_size -Xmx$max_heap_size -XX:PermSize=128m -Xloggc:gc.log -XX:+PrintGCTimeStamps -XX:-PrintGCDetails -cp $classpath net.csdn.bootstrap.Application  > application.log  &
  echo $! > application.pid
}
stop()
{
	if [ -d $DEPLOY_CURRENT ];then
		 echo "stoping system [`cd $DEPLOY_CURRENT;pwd -P`]....."
		 cd $DEPLOY_CURRENT
		 kill  `cat application.pid`
    fi
}

rollback()
{
  stop
  
  echo "rm -rf $DEPLOY_CURRENT"
  rm -rf $DEPLOY_CURRENT
  
  echo "rm -rf $DEPLOY_TO"
  rm -rf $DEPLOY_TO
  
  local preview_version=` ls $DEPLOY_ROOT |sort -r|head -n1`
  ln -s  $DEPLOY_ROOT/$preview_version $S_HOME/current
 
  start 
}


deploy()
{	
    cd $S_HOME
	
	echo "copy files to [$DEPLOY_TO]"
	for file in `ls $S_HOME`
	do
	    if [  $file != 'deploy' -a $file != 'current' ];then
	    echo "   coping $file ....."
	    cp -r $file $DEPLOY_TO
		valid_status $? "copy $file to $DEPLOY_TO "
	    fi
	done

	#开始编译啦
	javac -g -cp $classpath -d $DEPLOY_TO -encoding UTF-8 $sourcefiles
	
	#编译错误的退出脚本
	if [ $? -ne 0 ];then
		echo 'compile error !!! exit.....'
		exit $W_EXIT_STATUS
	fi
	cp -r "$S_HOME/src/META-INF" $DEPLOY_TO	
}

migrate_version()
{
	stop	
	rm -rf $DEPLOY_CURRENT
	ln -s  $DEPLOY_TO $DEPLOY_CURRENT 
    start
}

case $1 in
"restart")
   stop
   start
;;
"start")
   start
;;
"stop")
   stop
;;

"deploy")
   deploy
;;

"rollback")
   rollback
;;

"migrate_version")
   migrate_version
;;
*) echo "only accept params start|stop|restart|deploy" ;;
esac





