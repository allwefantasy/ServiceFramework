#!/bin/bash
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


compile()
{
 cd $1
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
 cat <<EOF
 sourcefiles=$sourcefiles
 classpath=$classpath
EOF
}