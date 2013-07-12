#! /bin/sh

# source shflags from current directory

. ./shflags

# define a 'name' command-line string flag
DEFINE_string 'enviroment'        'development'  '[optional:  production|development]'                 'e'
DEFINE_string 'command'           ''             '[required:  deploy|start|restart|stop|migrate_version|rollback] ' 'c'
DEFINE_string 'deploy_directory'  ''             '[optional:  usually name like 201301081057]'         'd'
DEFINE_string 'main_class'        ''             '[optional:  usually name like net.csdn.Application]' 't'

# parse the command-line
FLAGS "$@" || exit 1
eval set -- "${FLAGS_ARGV}"

cat <<EOF
DCP_ENV=${FLAGS_enviroment}
DCP_DEPLOY_DIR=${FLAGS_deploy_directory}
DCP_COMMAND=${FLAGS_command}
DCP_MainClass=${FLAGS_main_class}
EOF

