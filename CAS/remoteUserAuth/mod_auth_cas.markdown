### Requirements

- Front-end Apache server
- Cascade with AJP connector enabled in conf/server.xml
- mod_proxy_ajp


### Build and Install mod_auth_cas on Apache server

- Download latest stable version of [Jasig/Apereo’s mod_auth_cas project](https://github.com/apereo/mod_auth_cas)
- Unzip into its own directory
- Libraries required to compile: 
  - gcc
  - httpd24-devel
  - libcurl-devel
  - openssl-devel
  - pcre-devel
- Navigate to directory and run: 
  
  ```
  autoreconf -ivf
  .configure && make && sudo make install
  ```

If anything fails, please check that you have all of the module’s dependencies installed and then try compiling again. May be necessary to pass `--with-apxs24` flag specifying location of binaries to use. For example:

```
./configure --with-apxs24=/usr/sbin/apxs
```
  
### Configure AJP for Cascade and Apache

- Comment out the HTTP connector `tomcat/conf/server.xml` if you want to turn of HTTP connections to server
- Uncomment the AJP connector if it's not already
- Add a `tomcatAuthentication="false"` attribute to the AJP connector

### Example VirtualHost config

    LoadModule auth_cas_module modules/mod_auth_cas.so

    <VirtualHost *:80>
      CASDebug On
      LogLevel debug
      ErrorLog /var/log/httpd/cascade_error_log
      CustomLog /var/log/httpd/cascade_access_log combined

      CASLoginURL https://my.org/cas/login
      CASValidateURL https://my.org/cas/serviceValidate

      # Not validating the server for now
      CASCertificatePath /path/to/certificate

      #CASIdleTimeout 60
      #CASTimeout 60

      # path to a directoy writable by the webserver but not other processes
      # I created this directory in /var/cache for mod_auth_cas to use
      CASCookiePath /var/cache/mod_auth_cas/

      CASSSOEnabled On

      ServerName cas-enabled.cascadeserver.com
        
      # Protect all locations with CAS
      <Location />
        CASScope /
        AuthType CAS

        <RequireAny>
          Require all denied
          Require valid-user  
        </RequireAny>
      </Location>
      
      # Use this if your CAS does not support Single Sign-Out and you need 
      # to log people out of mod_auth_cas before redirecting to your 
      # organization's CAS logout service.
      # 
      # Instruct ProxyPass to _not_ proxy /logout url
      # /logout.php script should be used to destroy local mod_auth_cas
      # sessions before redirecting to the organization's CAS logout url
      #
      #ProxyPass /logout !
      #Alias /logout /var/www/cas-enabled.cascadeserver.com/logout.php
      
      <LocationMatch "^/(css|javascript|robots.txt|ajax/getLastBroadcastMessage.act)">
        Require all granted
      </LocationMatch>
      
      <LocationMatch "^/(api|ws)">
        <RequireAny>
          Require host any
        </RequireAny>
      </LocationMatch>

      ProxyPass         /websocket ws://<internal-ip>:8080/websocket
      ProxyPassReverse  /websocket ws://<internal-ip>:8080/websocket
      ProxyPass         / ajp://<internal-ip>:8009/
      ProxyPassReverse  / ajp://<internal-ip>:8009/
    </VirtualHost>
