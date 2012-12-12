Deploy.sh 脚本使用方式

./deploy.sh deploy version1 d

第一个参数:
deploy 代表编译部署操作
version1 你部署的版本好。字符或者数字皆可
d|p  d 为开发环境，p 为生产环境


./deploy.sh migrate_version version1

迁移到 version1 版本.并且启动运行。

重启服务
./deploy.sh restart

开启服务
./deploy.sh start

关闭应用
./deploy.sh stop



