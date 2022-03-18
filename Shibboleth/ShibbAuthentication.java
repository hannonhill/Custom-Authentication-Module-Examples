/**
 * This is based on the work of Andre Daniels (andre777 [at] ucsc [dot] edu) from UC Santa Cruz.
 *
 * @author Bradley Wagner
 * @author Andre Daniels
 * @author Ryan Griffith
 *
 * @description This is an implementation of the Cascade Server Authentication module that requires the user
 *              login via Shibboleth.
 * @see https://github.com/hannonhill/Custom-Authentication-Module-Examples/tree/master/Shibboleth
 */
package com.hannonhill.cascade.auth;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hannonhill.cascade.model.security.authentication.AuthenticationPhase;
import com.hannonhill.cascade.model.security.authentication.Authenticator;

public class CascadeShibAuth implements Authenticator
{
    private static Logger logger = LoggerFactory.getLogger(CascadeShibAuth.class);
    private static String LOGOUT_URL = "https://";

    private void log(String message)
    {
        logger.debug(message);
    }

    /**
     * Determines if the user should be redirected to a particular place during LOGIN or LOGOUT
     *
     * Use the reseponse to set a redirect location.
     *
     * @param request
     * @param response
     * @param authenticationPhase Indicates whether the user is logging in or logging out
     * @return Returns a boolean that indicates whether the user should be redirected or not
               If a user is not redirected during LOGIN, they will end up at the Cascade login screen.
     */
    public boolean redirect(HttpServletRequest request, HttpServletResponse response, AuthenticationPhase authenticationPhase) throws IOException
    {
        // If the Shibboleth attribute is not present in the request, this allows
        // login through the normal Cascade interface using a Normal authentication user.
        // This is useful for creating a backdoor into Cascade using a different port
        // or virtual host in Apache that does not require a Shibboleth session
        if (request.getAttribute("uid") == null)
        {
            log("No request attribute, uid, was found. Redirecting to Cascade CMS login screen.");
            return false;
          }

        log("Request attribute, uid, found. Processing authenticationPhase...");

        switch (authenticationPhase)
        {
            case LOGIN:
                log("Redirecting to custom auth servlet for authentication");
                // Redirect to the custom authentication servlet so that it can call this classes' authenticate()
                response.sendRedirect(Authenticator.AUTHENTICATION_URI);
                return true;
            case LOGOUT:
                log("Redirecting to custom logout URL: ", LOGOUT_URL);
                // Shibboleth doesn't support Single Sign-Out but this will ensure that
                // the local Shibboleth session is destroyed and that the system will have
                // to go back to the Shibboleth IdP to ensure the user is still logged in.
                response.sendRedirect(LOGOUT_URL);
                return true;
            default:
                // should never get here
                log("How did we get here!?");
                return false;
        }
    }

    /**
     * Allows the module to report back whether or not the user has been authenticated.
     *
     * @param request
     * @param response
     * @return the username if the user successfully authenticated
     */
    public String authenticate(HttpServletRequest request, HttpServletResponse response)
    {
        // Pull the appropriate attribute passed to it by Apache/AJP and return it
        log("Attribute: uid, Value: " + request.getAttribute("uid"));
        String username = (String) request.getAttribute("uid");

        int stripIndex = username.indexOf("@");
        if (stripIndex != -1)
        {
            log("Found @ symbol at index: " + stripIndex);
            String removed = username.substring(stripIndex);
    		log("Removing " + removed + " from uid value");
            username = username.substring(0, stripIndex);
        }

        // Optional if forcing lowercase
        //log("Forced lowercase is enabled, lowercasing " + username + " to " + username.toLowerCase());
        //username = username.toLowerCase();

        log("Authenticating with username: " + username);

        return username;
    }
}
