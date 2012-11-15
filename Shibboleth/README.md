# Shibboleth Authentication Module for Cascade Server

This authentication plugin leverages a Linux Shibboleth package and a Shibboleth authentication module for Apache to do most of the authentication legwork.

You'll need:

- Shibboleth 2 Service Provider
- Apache HTTP Server + mod_proxy_ajp + mod_shibb_22
- Tomcat AJP Connnector
- SSL certificates -- Shibboleth IdPs only like to talk to SPs over SSL

### Pre-reqs

- Ensure that your Apache version has mod_proxy_ajp installed. Most do by default.

### Install Shibboleth Service Provider

Find the [installation instructions](https://wiki.shibboleth.net/confluence/display/SHIB2/Installation) that correspond to your server environment. In most cases, this will be a [Linux install](https://wiki.shibboleth.net/confluence/display/SHIB2/NativeSPLinuxInstall)

I followed the [instructions for installing Shibboleth via `yum`](https://wiki.shibboleth.net/confluence/display/SHIB2/NativeSPLinuxRPMInstall#NativeSPLinuxRPMInstall-InstallingviaYum):

1. Find the `.repo` file for your distribution of Linux [from the opensuse repositories](http://download.opensuse.org/repositories/security://shibboleth/)
1. Place the appropriate `.repo` file in your `/etc/yum.repos.d` directory
2. `sudo yum list shibboleth` to find the package you want. Usually it’s shibboleth.x86_64 on a 64-bit server
3. `sudo yum install shibboleth`
4. Copy `/etc/shibboleth/apache22.config` to `/etc/httpd/conf.d` if it was not copied there automatically by the installation process. It was copied there for me as `/etc/httpd/conf.d/shibb.conf`

### Configure Apache in "worker" mode

Your mileage may vary depending on your distro and your version of Apache. 

Enable “worker” mode in Apache by:

1. Stopping Apache
2. Editing `/etc/sysconfig/httpd` and uncommenting the line: `HTTPD=/usr/sbin/httpd.worker`
2. Start Apache and `ps aux | grep worker` to make sure that httpd.worker is running.

### Configure Shibboleth to talk to your IdP

I followed the [SP getting started guide](Follow https://wiki.shibboleth.net/confluence/display/SHIB2/NativeSPGettingStarted) and the [Java Servlets guide](https://wiki.shibboleth.net/confluence/display/SHIB2/NativeSPJavaInstall)

1. Edit `/etc/shibboleth/shibboleth2.xml`
2. Set a unique `entityId` for `<ApplicationDefaults>` -- e.g. https://cascade.yourorg.com/shibboleth
3. Set the support contact email to: support@yourorg.com (will be support@hannonhill.com for Hannon Hill's hosted instances)
4. Add: `attributePrefix="AJP_"` attribute to the `<ApplicationDefaults>`. This is necessary when proxying via `mod_proxy_ajp` as AJP will only send environment variables with an "AJP_" prefix to Tomcat.
5.  Curl the organization’s metadata: e.g. `curl -k http://yourorg.com/path/to/metadata -o /etc/shibboleth/example-metadata.xml` to have a local copy.
6. If the metadata is publicly accessible via the web, add the appropriate `uri` and `backingFile` attributes to `<MetadataProvider>`:
    
    <!-- Example of remotely supplied batch of signed metadata. →
    <MetadataProvider type="XML" uri="https://shibb.yourorg.com/idp/shibboleth" backingFile="example-metadata.xml" reloadInterval="7200">
    </MetadataProvider>
         
7. If the organization does not make their metadata publicly available through a URI, you can download the metadata and point Shibboleth to the file with the `path` attribute.
8. I also had to remove the MetadataFilter sub-elements to get this to work correctly
9. Find out what the appropriate attribute is in Shibboleth for what will be your Cascade usernames and make sure that attribute is mapped in `/etc/shibboleth/attribute-map.xml`. See our [attribute-map.xml](https://github.com/hannonhill/Custom-Authentication-Module-Examples/tree/master/Shibboleth/attribute-map.xml) for an example. In our case, it required adding:

        <Attribute name="urn:oid:0.9.2342.19200300.100.1.1" id="uid">
          <AttributeDecoder xsi:type="StringAttributeDecoder"/>
        </Attribute>

10. Verify your changes by restarting shibboleth: `sudo /etc/init.d/shibd restart` and making sure everything is `OK`
11. Create some SP metadata to add to your Shibboleth Identity Provider since we’re not a member of any federations:

        cd /etc/shibboleth
        sudo ./metagen.sh -c /etc/shibboleth/blah.cascadeserver.com.crt -h blah.cascadeserver.com -e https://blah.cascadeserver.com/shibboleth > metadata.xml

12. Send the metadata to someone who can install it on the Shibboleth IdP end.

### Configure Tomcat for AJP

Review the [Java install docs](https://wiki.shibboleth.net/confluence/display/SHIB2/NativeSPJavaInstall) again for more info.

On your Cascade Server machine:

1. Uncomment the AJP `<Connector>` element in `tomcat/conf/server.xml` that has `protocol=”AJP/1.3”`.
2. Add a `tomcatAuthentication=”false”` attribute to the `<Connector>` element too
3. Comment out the HTTP `<Connector>` element with `protocol=”HTTP/1.1”` to ensure that traffic is going through the AJP port
4. Add `packetSize=”65536”` to AJP <Connector> element
5. Go back to Apache and add “ProxyIOBufferSize 65536” somewhere in Apache HTTP’s configuration to allow larger packets to be passed to Tomcat. I did this in the file where I declared my Cascade `<VirtualHost>` below

### Configure Apache to route traffic to Tomcat

First, you need to set Cascade to run over SSL

1. Generate a self-signed cert or purchase a cert
2. Ensure that Apache is listening on port 443 -- i.e. `Listen 443` in `/etc/httpd/conf/http.conf
3. Enable `NameVirtualHost *:443` in `/etc/httpd/conf/http.conf`
4. Ensure that `mod_proxy_ajp` is installed and enabled -- i.e. `LoadModule proxy_ajp_module modules/mod_proxy_ajp.so` in `/etc/httpd/conf/http.conf` is uncommented
5. Create a `<VirtualHost>` in a `.conf` file somewhere `/etc/httpd/conf.d`. In our case, this just required adding one to `/etc/httpd/conf.d/ssl.conf`:
    
        <VirtualHost *:443>
          ServerName blah.cascadeserver.com
          ProxyPass / ajp://localhost:8009/
          ProxyPassReverse / http://localhost:8009/
          SSLProxyEngine on
          SSLEngine on
          ServerAdmin support@hannonhill.com
          ErrorLog logs/blah.cascadeserver.com-error_log
          TransferLog logs/blah.cascadeserver.com-transfer_log
          CustomLog logs/blah.cascadeserver.com-access_log \
            "%t %h %{SSL_PROTOCOL}x %{SSL_CIPHER}x \"%r\" %b"
          SSLCipherSuite HIGH:MEDIUM
          SSLCipherSuite  ALL:!ADH:!EXPORT56:RC4+RSA:+HIGH:+MEDIUM:+LOW:+SSLv2:+EXP:+eNULL
          SSLProtocol all -SSLv2
          SSLCertificateFile /etc/shibboleth/blah.cascadeserver.com.crt
          SSLCertificateKeyFile /etc/shibboleth/blah.cascadeserver.com.key
          SSLOptions +StdEnvVars
          BrowserMatch ".*MSIE.*" \ nokeepalive ssl-unclean-shutdown \ downgrade-1.0 force-response-1.0
        
          <Location />
            AuthType shibboleth
            ShibRequestSetting requireSession 1
            require valid-user
          </Location>
        </VirtualHost>
        
6. Notice we're referencing the path to our `SSLCertificateFile` and `SSLCertificateKeyFile` in the configuration above
7. We also added a `<Location>` block to restrict access to all paths to users that have a valid Shibboleth session.

### Writing and Enabling a Custom Authentication plugin in Cascade

Once the above is done, you should be able to connect to your Cascade server instance, be redirected to your Shibboleth server, login and be redirected back to Cascade. You'll likely arrive at the login screen.

The last step is writing a Custom Authentication plugin and enabling it in Cascade. See [our example plugin](https://github.com/hannonhill/Custom-Authentication-Module-Examples/tree/master/Shibboleth/ShibbAuthentication.java) here.

A few notes:

- In our case, we were using the `uid` attribute to hold the username
- By not redirecting if there is no Shibboleth attribute present, we maintained a backdoor so that we could connect to Cascade on a different port or using a different Virtual Host and login using the normal login screen and a user/pass with Normal authentication.
- In authenticate(), we're pulling the appropriate attribute out of the request using: `request.getAttribute()`. The name of the attribute should match the `id` attribute of the `<Attribute>` in your Shibboleth `attribute-map.xml` above.

You can read more about writing Custom Authentication plugin classes in our [Cascade Server Authentication API project](https://github.com/hannonhill/Cascade-Server-Authentication-API)

To install the plugin in Cascade:

1. Make a JAR containing your implementation of the Authenticator interface
2. Stop Cascade
3. Drop the JAR into `$CASCADE_HOME/tomcat/webapps/ROOT/WEB-INF/lib`
4. Start Cascade
5. Login into Cascade. NOTE: You will need a user with the Administration role that uses Normal authentication to be able to login.
6. Go to System Menu > Configuration > Custom Authentication
7. Add the following configuration and substitute the `<class-name>` for the package-qualified classname of your plugin class that you wrote:

        <custom-authentication-module>
          <class-name>com.hannonhill.cascade.shibb.ShibbAuthentication</class-name>
          <should-intercept-login-page>true</should-intercept-login-page>
        </custom-authentication-module>
        
8. Attempt to connect to your instance again. After authenticating in Shibboleth you should be automatically taken into Cascade.





