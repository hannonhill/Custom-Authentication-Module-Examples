/*
 * RemoteUser authentication module for Cascade
 *
 * This module lets you use Tomcat or Apache authentication
 * with Cascade.
 *
 * February 2008, Earl Fogel, University of Saskatchewan
 */
package ca.usask.cascade;

import java.io.*;
import javax.servlet.http.*;
import com.hannonhill.cascade.model.security.authentication.*;

public class remoteuserauth extends java.lang.Object
    implements
	com.hannonhill.cascade.model.security.authentication.Authenticator {

    /* Constructors */
    public remoteuserauth() {
    }

    /* Methods */
    public boolean redirect(HttpServletRequest request, HttpServletResponse response, AuthenticationPhase phase) throws IOException {

	/*
	 * User has not authenticated, show Cascade login screen.
	 */
	if (request.getRemoteUser() == null ) {
	    return false;
	}

	/*
	 * User has authenticated with web server.
	 * Redirect to customauth for login, somewhere else for logout.
	 */
	if (phase == AuthenticationPhase.LOGIN) {
	    response.sendRedirect("/customauth");
	    return true;
	} else {
	    response.sendRedirect("http://wcms.usask.ca/logout.html");
	    return true;
	}
    }

     public String authenticate(HttpServletRequest request, HttpServletResponse response) {
	return request.getRemoteUser();
   }
}
