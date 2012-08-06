#!/bin/bash
. /etc/profile

min_heap_size="1300m"
max_heap_size="1300m"

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

targetdir=`dirname "$SCRIPT"`
targetdir=`cd $targetdir; pwd`

cd $targetdir

if [ "$1" == "start" ];then
  ./compile.sh
fi

cd $targetdir
cd ../build

classpath=.
for jarfile in `ls lib`
do
    classpath=$classpath:lib/$jarfile
done
start()
{
  nohup java -Xms$min_heap_size -Xmx$max_heap_size -XX:PermSize=128m -Xloggc:gc.log -XX:+PrintGCTimeStamps -XX:-PrintGCDetails -cp $classpath net.csdn.bootstrap.Application  > service_framework.log  &
  echo $! > service_framework.pid
}
stop()
{
  kill  `cat service_framework.pid`
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
*) echo "only accept params start|stop|restart" ;;
esac
