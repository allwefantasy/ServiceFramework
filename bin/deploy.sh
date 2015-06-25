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

CONDITION_FAIL=1
SUCCESS=0

PROJECT_NAME=${2:-'search_system_v2'}

S_HOME=$(cd `dirname "$SCRIPT"`/..;pwd)
S_CONFIGURATION_HOME=/data/configurations/${PROJECT_NAME}/config
S_TARGET=${S_HOME}/release
S_DEPLOY=${S_HOME}/deploy

source ${S_HOME}/bin/config

echo "部署变量"
echo "S_HOME=${S_HOME}"
echo "S_CONFIGURATION_HOME=${S_CONFIGURATION_HOME}"
echo "S_TARGET=${S_TARGET}"
echo "S_DEPLOY=${S_DEPLOY}"
echo "s_main_class=${s_main_class}"
echo "s_java_options=${s_java_options}"
echo "application_file=${application_file}"

check_dir_empty(){
   [ "$(ls -A $1)" ] && return ${CONDITION_FAIL} || return ${SUCCESS}
}

check_install(){
  echo "检查 配置文件，依赖包，已编译的class文件...."
  [ ! -f ${S_CONFIGURATION_HOME}/${application_file} ] && echo "$S_CONFIGURATION_HOME/${application_file} 文件不存在" && return ${CONDITION_FAIL}
  check_dir_empty ${S_TARGET}  && echo "$S_TARGET 文件夹不存在" && return
  check_dir_empty ${S_TARGET}/dependency && echo "$S_TARGET/dependency => 没有检查到依赖包" && return ${CONDITION_FAIL}
  check_dir_empty ${S_TARGET}/classes && echo "$S_TARGET/classes => 没有检查到项目依赖" && return ${CONDITION_FAIL}
  echo "检查没有问题 继续..."
  return ${SUCCESS}
}

write_deploy_version(){
   echo ${1} > ${S_HOME}/deploy_version
}

fetch_deploy_version(){
   echo `cat ${S_HOME}/deploy_version`
}

clean(){
  echo "清理${S_DEPLOY}目录"
  rm -rf ${S_DEPLOY}
  echo "删除运行部署版本记录"
  write_deploy_version
}
deploy(){

   ! check_install && return  ${CONDITION_FAIL}
   local S_DEPLOY_VERSION_TIME=`date +%Y%m%d%H%M%S`
   write_deploy_version ${S_DEPLOY_VERSION_TIME}

   [ ! -d ${S_DEPLOY} ] && mkdir -p ${S_DEPLOY}
   [ ! -d ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME} ] && mkdir -p ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}

   mkdir ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/logs
   mkdir ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/config

   cp -r ${S_TARGET}/dependency  ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/
   cp -r ${S_TARGET}/classes  ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/

   cp -r ${S_HOME}/dictionaries ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/dictionaries
   cp -r ${S_HOME}/template ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/template

   rm ${S_HOME}/current
   ln -s ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}  ${S_HOME}/current
   ln -s ${S_CONFIGURATION_HOME}/${application_file} ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/config/application.yml
   ln -s ${S_CONFIGURATION_HOME}/strategy.v2.json ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/config/strategy.v2.json
   ln -s ${S_CONFIGURATION_HOME}/logging.yml ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/config/logging.yml

   return ${SUCCESS}
}

start()
{
  if [ -z `fetch_deploy_version` ];then
    echo "还没有部署过，进入部署过程"
    deploy
    [ $? -ne 0 ] && echo "部署失败，无法启动应用" && return
  fi

  local S_DEPLOY_VERSION_TIME=`fetch_deploy_version`
  cd ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}
  rm ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/config/application.yml
  ln -s ${S_CONFIGURATION_HOME}/${application_file} ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}/config/application.yml

  local PIDS=`cat application.pid`
  if [ -n "$PIDS" ]; then
    echo "ERROR: already started!"
    echo "PID: $PIDS"
    exit 1
  fi

  echo -e "Starting ...\c"
  echo "java ${s_java_options} -Xloggc:gc.log -XX:+PrintGCTimeStamps -XX:-PrintGCDetails -cp dependency/*:classes ${s_main_class}"
  nohup java ${s_java_options} -Xloggc:gc.log -XX:+PrintGCTimeStamps -XX:-PrintGCDetails -cp dependency/*:classes ${s_main_class} > /dev/null 2>&1 &
  echo $! > application.pid
  echo "进程号为: `cat application.pid`"

#  local COUNT=0
#  while [ $COUNT -lt 1 ]; do
#    echo -e ".\c"
#    sleep 1
#    if [ -n "$SERVER_PORT" ]; then
#        if [ "$SERVER_PROTOCOL" == "dubbo" ]; then
#    	    COUNT=`echo status | nc -i 1 127.0.0.1 $SERVER_PORT | grep -c OK`
#        else
#            COUNT=`netstat -an | grep $SERVER_PORT | wc -l`
#        fi
#    else
#    	COUNT=`ps -f | grep java | grep "$DEPLOY_DIR" | awk '{print $2}' | wc -l`
#    fi
#    if [ $COUNT -gt 0 ]; then
#        break
#    fi
#  done
#  echo "OK!"
}
stop()
{
    local S_DEPLOY_VERSION_TIME=`fetch_deploy_version`
    cd ${S_DEPLOY}/${S_DEPLOY_VERSION_TIME}
    local pid=`cat application.pid`
    echo "获取进程pid: ${pid}"
    [ ! -z ${pid} ] && kill -15 ${pid} || echo "无法获取pid"
    sleep 3
}

rollback()
{
  local versions=($(ls ${S_DEPLOY} |sort -r|head |tr "\n" " "))
  write_deploy_version ${versions[1]}
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

"clean")
   clean
;;

*) echo "only accept params start|stop|restart|deploy|rollback|clean" ;;
esac

exit 0




