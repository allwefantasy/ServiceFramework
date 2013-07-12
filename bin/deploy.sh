#!/bin/bash
#加载环境变量
source /etc/profile

# 执行该脚本时 有可能是符号链接 我们要找到它的实际位置


 SCRIPT=$0
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

cd $S_HOME/bin

source functions.sh
eval `./deploy_command_parser.sh $@`

echo $DCP_ENV $DCP_DEPLOY_DIR $DCP_COMMAND $DCP_MainClass

S_ENV=$DCP_ENV
S_DEPLOY_DIR=${DCP_DEPLOY_DIR}
S_COMMAND=$DCP_COMMAND
S_MainClass=${DCP_MainClass}

#make sure these variables will not harm
unset DCP_ENV DCP_DEPLOY_DIR DCP_COMMAND DCP_MainClass

#默认我们设置为开发环境 p env=p 为生产环境  env=d 为开发环境
env=$S_ENV
build_dir=${S_DEPLOY_DIR:-`ls $DEPLOY_ROOT |sort -r|head -n1`}

#虚拟机参数
min_heap_size="1300m"
max_heap_size="1300m"

#生产环境设置
if [ $S_ENV == "production" ];then
	min_heap_size="1300m"
	max_heap_size="1300m"
fi

#异常退出code
W_EXIT_STATUS=65

DEPLOY_ROOT="$S_HOME/deploy"
DEPLOY_TO="$S_HOME/deploy/$build_dir"
DEPLOY_CURRENT="$S_HOME/current"

s_mkdir $DEPLOY_ROOT
s_mkdir $DEPLOY_TO



#进入根目录
cd $S_HOME
echo "ROOT Directory => $S_HOME"

compile $S_HOME

start()
{
  echo "staring system [`cd $DEPLOY_CURRENT;pwd -P`]....."
  cd $DEPLOY_CURRENT
  nohup java -Xms$min_heap_size -Xmx$max_heap_size -XX:PermSize=128m -Xloggc:gc.log -XX:+PrintGCTimeStamps -XX:-PrintGCDetails -cp $classpath $S_MainClass  > /dev/null 2>&1  &
  echo $! > application.pid
}
stop()
{
	if [ -d $DEPLOY_CURRENT ];then
		 echo "stoping system [`cd $DEPLOY_CURRENT;pwd -P`]....."
		 cd $DEPLOY_CURRENT
		 kill  `cat application.pid`
    fi
    sleep 3
}

rollback()
{
  stop
  
  echo "rm -rf $DEPLOY_CURRENT"
  rm -rf $DEPLOY_CURRENT
  
  mv $DEPLOY_TO $DEPLOY_TO"_fail"
  
  local preview_version=`ls $DEPLOY_ROOT |sort -r|head -n1`
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
	javac -g -cp $classpath -d $DEPLOY_TO -encoding UTF-8  $sourcefiles
	
	#编译错误的退出脚本
	valid_status $? "compile error !!! exit....."
	
	if [ -d "$S_HOME/src/META-INF" ];then
	   cp -r "$S_HOME/src/META-INF" $DEPLOY_TO	
    fi
}

migrate_version()
{
	stop	
	rm -rf $DEPLOY_CURRENT
	ln -s  $DEPLOY_TO $DEPLOY_CURRENT 
    start
}

case $S_COMMAND in
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

exit 0




