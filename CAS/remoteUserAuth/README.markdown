RemoteUser authentication for Cascade
=====================================

The RemoteUser authentication module lets you use various web server 
authentication methods with Cascade.  The plugin itself is very simple 
as it relies on the web server to do the actual authentication. 

When users connect to Cascade, the module checks if the web server has 
already authenticated them.  If the user has authenticated, they get 
into Cascade, if not, they see the normal Cascade login screen. 

In our case, we run Apache in front of Tomcat, and do CAS authentication 
with the mod_auth_cas Apache authentication module.  I also have a 
couple of patches for that module that I can share if you are interested. 
If you don't use Apache, you can do authentication in Tomcat instead. 

We have two virtual hosts set up in Apache -- one requires CAS 
authentication, and the other does not.  This lets us use CAS for regular 
users and use internal Cascade authentication for test accounts and web 
services. 

Before building the plugin, you need to change one line of the java 
source code. Line 41 currently reads:

     response.sendRedirect("http://wcms.usask.ca/logout.html");

Change that to the logout URL for your own site.

To build the plugin, download the most recent authentication-x.x.jar file from 
Hannon Hill.

Next, set your java CLASSPATH to include this jar as well as Tomcat's 
servlet-api.jar file and run:

     javac remoteuserauth.java

Then, since the plugin says "package ca.usask.cascade", put the resulting 
class file in an equivalent directory and build a jar file: 

     mkdir ca
     mkdir ca/usask
     mkdir ca/usask/cascade
     mv remoteuserauth.class ca/usask/cascade
     jar cf remoteuserauth.jar ca/usask/cascade/remoteuserauth.class

Now copy that jar file into your Cascade server's 
tomcat/webapps/ROOT/WEB-INF/lib/ folder and restart Cascade.

Now login to Cascade, go into the Cascade Configuration/Custom 
Authentication screen and paste in this information: 

     <custom-authentication-module>
     <class-name>ca.usask.cascade.remoteuserauth</class-name>
     <should-intercept-login-page>true</should-intercept-login-page>
     </custom-authentication-module>

Finally, restart Cascade once more.

Note: If you are using ajp to communicate between Apache and Tomcat, you 
also need to specify tomcatAuthentication="false" to the AJP connector in 
your tomcat server.xml file.

Earl Fogel <earl.fogel@usask.ca>
University of Saskatchewan
