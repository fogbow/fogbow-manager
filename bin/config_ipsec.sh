config_setup="config setup\n charondebug="all"\n uniqueids=yes\n strictcrlpolicy=no\nconn %default\nconn tunnel"
left=$1
leftid=$2
leftsubnet=$3
tunnel_param_configs=" left="$left"\n leftid"=$leftid"\n leftsubnet="$leftsubnet"\n right=%any\n rightsourceip=%config"
other_tunnel_configs=" ike=aes256-sha2_256-modp1024!\n esp=aes256-sha2_256!\n keyingtries=0\n ikelifetime=1h\n lifetime=8h\n dpddelay=30\n dpdtimeout=120\n dpdaction=restart\n authby=secret\n auto=start\n keyexchange=ikev2\n type=tunnel"

echo -e $config_setup'\n'$tunnel_param_configs'\n'$other_tunnel_configs > /etc/ipsec.conf
sysctl net.ipv4.ip_forward=1
sysctl net.ipv6.conf.all.forwarding=1
ipsec restart
