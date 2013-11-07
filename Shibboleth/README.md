# Shibboleth Authentication Module for Cascade Server

This authentication plugin leverages a Linux Shibboleth package and a Shibboleth authentication module for Apache to do most of the authentication legwork.

The plugin simply pulls the attribute containing the username out of the request. It is also responsible for invalidating the session on logout.

### Pre-reqs

- Ensure that your Apache version has mod_proxy_ajp installed. Most do by default.
- Shibboleth 2 Service Provider
- SSL certificates -- Shibboleth IdPs only like to talk to SPs over SSL

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

I followed the [SP getting started guide](https://wiki.shibboleth.net/confluence/display/SHIB2/NativeSPGettingStarted) and the [Java Servlets guide](https://wiki.shibboleth.net/confluence/display/SHIB2/NativeSPJavaInstall)

1. Edit `/etc/shibboleth/shibboleth2.xml`
2. Set a unique `entityId` for `<ApplicationDefaults>` -- e.g. https://cascade.yourorg.com/shibboleth
3. Set the support contact email (`supportContact` attribute on `<Errors>` element) to: support@yourorg.com (will be support@hannonhill.com for Hannon Hill's hosted instances)
4. Add: `attributePrefix="AJP_"` attribute to the `<ApplicationDefaults>`. This is necessary when proxying via `mod_proxy_ajp` as AJP will only send environment variables with an "AJP_" prefix to Tomcat.
5. Curl the organization’s metadata: e.g. `curl -k http://yourorg.com/path/to/metadata -o /etc/shibboleth/example-metadata.xml` to have a local copy.
6. Uncomment the `<MetadataProvider>` element in shibboleth2.xml 
6. If the metadata is publicly accessible via the web, add the appropriate `uri` and `backingFile` attributes to `<MetadataProvider>`:

         <!-- Example of remotely supplied batch of signed metadata. -->
         <MetadataProvider type="XML" uri="https://shibb.yourorg.com/idp/shibboleth" backingFile="example-metadata.xml" reloadInterval="7200">
         </MetadataProvider>

7. If the organization does not make their metadata publicly available through a URI, you can download the metadata and point Shibboleth to the file with the `path` attribute.

         <!-- Example of remotely supplied batch of signed metadata. -->
         <MetadataProvider type="XML" path="/path/to/their/local/metadata.xml" reloadInterval="7200">
         </MetadataProvider>
     
8. I also had to remove the MetadataFilter sub-elements to get this to work correctly
9. Find out what the appropriate attribute is in Shibboleth for what will be your Cascade usernames and make sure that attribute is mapped in `/etc/shibboleth/attribute-map.xml`. See our [attribute-map.xml](https://github.com/hannonhill/Custom-Authentication-Module-Examples/tree/master/Shibboleth/attribute-map.xml) for an example. A alot of times, you can see which attributes the IdP exposes by going to: https://idp.blah.com/attribute-map.xml. You'll still need to find out which attribute corresponds to the Cascade username. In our case, it required adding:

        <Attribute name="urn:oid:0.9.2342.19200300.100.1.1" id="uid">
          <AttributeDecoder xsi:type="StringAttributeDecoder"/>
        </Attribute>

6. Uncomment the `<SSO>` element and set the `entityId` to match the `entityId` in the provided metadata of the IdP
10. Verify your changes by restarting shibboleth: `sudo /etc/init.d/shibd restart` and making sure everything is `OK`
11. Create some SP metadata to add to your Shibboleth Identity Provider since we’re not a member of any federations. You'll need a valid SSL cert in order to be able to generate the metadata.

        cd /etc/shibboleth
        sudo ./metagen.sh -c /path/to/blah.cascadeserver.com.ssl.crt -h blah.cascadeserver.com -e https://blah.cascadeserver.com/shibboleth > ~/metadata.xml

12. Send the metadata to someone who can install it on the Shibboleth IdP end.
13. (Optional) In some cases, you may need to uncomment the `<CredentialResolver>` and set the "key" and "certificate" attributes to point to the SSL key and cert. You'll get a message in the shibd.log about needing to decrypt the message if this step is required.

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
5. Create a `<VirtualHost>` in a `.conf` file om `/etc/httpd/conf.d`. In our case, this just required adding one to `/etc/httpd/conf.d/ssl.conf`:

        <VirtualHost *:443>
          ServerName blah.cascadeserver.com
          
          SSLProxyEngine on
          SSLEngine on
          ServerAdmin support@hannonhill.com
                 
          SSLCertificateFile /path/to/blah.cascadeserver.com.crt
          SSLCertificateKeyFile /path/to/blah.cascadeserver.com.key
                
          <Location />
            AuthType shibboleth
            ShibRequestSetting requireSession 1
            require valid-user
          </Location>

          ProxyPass / ajp://localhost:8009/
          ProxyPassReverse / http://localhost:8009/
        </VirtualHost>
        
6. Notice we're referencing the path to our `SSLCertificateFile` and `SSLCertificateKeyFile` in the configuration above
7. We also added a `<Location>` block to restrict access to all paths to users that have a valid Shibboleth session.

Next, you need to make sure that the `/Shibboleth.sso/*` URLs do not require a `valid-user` or you will get stuck in a loop.

The config file: apache22.conf that gets copied over on install contains:

```
<Location /Shibboleth.sso>
  Satisfy Any
  Allow from all
</Location>
```

which you can simply remove. The shib Apache module appears to be smart enough to know not to protect: /Shibboleth.sso/* URLs even thought we're not explicitly exposing them.

Once you've got your configuration in place, test to make sure that you can get to: `https://blah.cascadeserer.com/Shibboleth.sso/Session`. If you cannot, you'll need to remove other parts of the config until you can hit that URL. Remove your VirtualHost and 
add back one piece at a time while checking that you can still hit `https://blah.cascadeserer.com/Shibboleth.sso/Session`.

### Writing and Enabling a Custom Authentication plugin in Cascade

Once the above is done, you should be able to connect to your Cascade server instance, be redirected to your Shibboleth server, login and be redirected back to Cascade. You'll likely arrive at the login screen.

The last step is writing a Custom Authentication plugin and enabling it in Cascade. 

You can use/modify our [our example plugin](https://github.com/hannonhill/Custom-Authentication-Module-Examples/tree/master/Shibboleth/ShibbAuthentication.java) here.

A few notes:

- In our case, we were using the `uid` attribute to hold the username
- By not redirecting if there is no Shibboleth attribute present, we maintained a backdoor so that we could connect to Cascade on a different port or using a different Virtual Host and login using the normal login screen and a user/pass with Normal authentication.
- In authenticate(), we're pulling the appropriate attribute out of the request using: `request.getAttribute()`. The name of the attribute should match the `id` attribute of the `<Attribute>` in your Shibboleth `attribute-map.xml` above.

You can read more about writing Custom Authentication plugin classes in our [Cascade Server Authentication API project](https://github.com/hannonhill/Cascade-Server-Authentication-API)

#### Modify the plugin for your organization

1. Update the name of the attribute containing your usernames that you're passing to Tomcat (e.g. `uid`) in both the `redirect()` and `authenticate()` methods.
2. Update URL being redirected to in `redirect()` during the `LOGOUT` phase if you have another script or location that you wish to send your users
3. (Optional) Change the package name

NOTE: There is nothing in this plugin containing the URL of the Shibboleth IdP as this is all being handled in Apache.

#### Compiling the plugin 

Requires the following JARs be located in this directory for compilation:

- Tomcat's [servlet-api.jar](https://github.com/hannonhill/Custom-Authentication-Module-Examples/tree/master/Shibboleth/tomcat-6.0.32-servlet-api.jar) or you can copy it from Cascade's `tomcat/lib` directory
- Log4J's [log4j.jar](https://github.com/hannonhill/Custom-Authentication-Module-Examples/tree/master/Shibboleth/log4j-1.2.14.jar) or you can copy it from your Cascade `tomcat/webapps/ROOT/WEB-INF/lib` directory
- Cascade [authentication.jar](https://github.com/hannonhill/Cascade-Server-Authentication-API/tree/master/dist)
    
1. Compile the Java class: `javac -classpath log4j-1.2.14.jar:tomcat-6.0.32-servlet-api.jar:authentication-7.0.jar ShibbAuthentication.java -d .` -- Compiles `.java` files into `.class` files into a sub-directory based on the package name (e.g. `com/hannonhill/cascade/shibb`)
2. JAR up the class files: `jar cf shib-plugin.jar com` where "com" is the directory containing your class files

#### Install the plugin 

1. Stop Cascade
2. Drop the compiled JAR into `$CASCADE_HOME/tomcat/webapps/ROOT/WEB-INF/lib`
3. Start Cascade
4. Login into Cascade. NOTE: You will need a user with the Administration role that uses Normal authentication to be able to login.
5. Go to System Menu > Configuration > Custom Authentication
6. Add the following configuration and substitute the `<class-name>` for the package-qualified classname of your plugin class that you wrote:

        <custom-authentication-module>
          <class-name>com.hannonhill.cascade.shibb.ShibbAuthentication</class-name>
          <should-intercept-login-page>true</should-intercept-login-page>
        </custom-authentication-module>
        
7. Attempt to connect to your instance again. After authenticating in Shibboleth you should be automatically taken into Cascade.
