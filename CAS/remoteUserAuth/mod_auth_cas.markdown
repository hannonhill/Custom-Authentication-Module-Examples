### Requirements

- Front-end Apache server
- Cascade with AJP connector enabled in conf/server.xml
- mod_proxy_ajp


### Build and Install mod_auth_cas on Apache server

- Download latest tag of Jasig’s mod_auth_cas project
- Unzip into its own directory
- Navigate to directory and run: `.configure; make; make install`
  If anything fails, please check that you have all of the module’s dependencies installed and then try compiling again. May be necessary to pass “--with-apxs” flag specifying location of binaries to use. For example:
  ./configure --with-apxs=/usr/sbin/apxs
  When I did this, I had to install: openssl-devel, httpd-devel, and curl-devel before ./configure would run successfully
- 

