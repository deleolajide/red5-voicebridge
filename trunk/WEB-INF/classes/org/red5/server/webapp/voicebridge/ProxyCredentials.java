package org.red5.server.webapp.voicebridge;


public class ProxyCredentials
{
    private String userName = null;
    private String userDisplay = null;
    private char[] password = null;
    private String authUserName = null;
    private String realm = null;
    private String proxy = null;
    private String host = null;

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public void setAuthUserName(String userName)
    {
        this.authUserName = userName;
    }

    public void setRealm(String realm)
    {
        this.realm = realm;
    }

    public void setProxy(String proxy)
    {
        this.proxy = proxy;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public void setUserDisplay(String userDisplay)
    {
        this.userDisplay = userDisplay;
    }

    public void setPassword(char[] passwd) {
        this.password = passwd;
    }

    public String getUserDisplay()
    {
        return userDisplay;
    }

    public String getUserName()
    {
        return this.userName;
    }

    public String getRealm()
    {
        return realm;
    }

    public String getProxy()
    {
        return proxy;
    }

    public String getHost()
    {
        return host;
    }

    public String getAuthUserName()
    {
        return this.authUserName;
    }

    public char[] getPassword() {
        return password;
	}

}
