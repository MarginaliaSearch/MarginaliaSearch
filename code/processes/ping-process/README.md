The ping process (which has nothing to do with ICMP ping) keeps track of 
the aliveness of websites.  It also gathers fingerprint information about
the security posture of the website, as well as DNS information.

This is kept to build an idea of when a website is down, and to identify
ownership changes, as well as other significant events in the lifecycle
of a website.

# Central Classes

* [PingMain](java/nu/marginalia/ping/PingMain.java) main class.
* [PingJobScheduler](java/nu/marginalia/ping/PingJobScheduler.java) service that dispatches pings.
