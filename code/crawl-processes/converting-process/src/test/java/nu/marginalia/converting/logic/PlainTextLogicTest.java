package nu.marginalia.converting.logic;

import nu.marginalia.converting.processor.logic.PlainTextLogic;
import nu.marginalia.converting.util.LineUtils;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

class PlainTextLogicTest {

    PlainTextLogic ptl = new PlainTextLogic();

    String uml = """
              User Mode Linux HOWTO
              User Mode Linux Core Team
              Fri Mar  7 11:53:53 EST 2008
                        
              This document describes the use and abuse of Jeff Dike's User Mode
              Linux: a port of the Linux kernel as a normal Intel Linux process.
              ______________________________________________________________________
                        
              Table of Contents
                        
                        
                        
              1. Introduction
                 1.1 What is User Mode Linux?
                 1.2 How is User Mode Linux Different?
                 1.3 How does UML Work?
                 1.4 Why Would I Want UML?
                        
              2. Compiling the kernel and modules
                 2.1 Compiling the kernel
                 2.2 Compiling and installing kernel modules
                 2.3 Compiling and installing uml_utilities
                        
              3. Running UML and logging in
                 3.1 Running UML
                 3.2 Logging in
                 3.3 Examples
                        
              4. UML on 2G/2G hosts
                 4.1 Introduction
                 4.2 The problem
                 4.3 The solution
                        
              5. Setting up serial lines and consoles
                 5.1 Specifying the device
                 5.2 Specifying the channel
                 5.3 Examples
                        
              6. Setting up the network
                 6.1 General setup
                 6.2 Userspace daemons
                 6.3 Specifying ethernet addresses
                 6.4 UML interface setup
                 6.5 Multicast
                 6.6 TUN/TAP with the uml_net helper
                 6.7 TUN/TAP with a preconfigured tap device
                 6.8 Ethertap
                 6.9 The switch daemon
                 6.10 Slip
                 6.11 Slirp
                 6.12 pcap
                 6.13 Setting up the host yourself
                        
              7. Sharing Filesystems between Virtual Machines
                 7.1 A warning
                 7.2 Using layered block devices
                 7.3 Note!
                 7.4 Another warning
                 7.5 Moving a backing file
                 7.6 uml_moo : Merging a COW file with its backing file
                 7.7 uml_mkcow : Create a new COW file
                        
              8. Creating filesystems
                 8.1 Create the filesystem file
                 8.2 Assign the file to a UML device
                 8.3 Creating and mounting the filesystem
                        
              9. Host file access
                 9.1 Using hostfs
                 9.2 hostfs command line options
                 9.3 hostfs as the root filesystem
                 9.4 Building hostfs
                        
              10. The Management Console
                 10.1 version
                 10.2 halt and reboot
                 10.3 config
                 10.4 remove
                 10.5 sysrq
                 10.6 help
                 10.7 cad
                 10.8 stop
                 10.9 go
                 10.10 log
                 10.11 proc
                 10.12 Making online backups
                 10.13 Event notification
                        
              11. Kernel debugging
                 11.1 Starting the kernel under gdb
                 11.2 Examining sleeping processes
                 11.3 Running ddd on UML
                 11.4 Debugging modules
                 11.5 Attaching gdb to the kernel
                 11.6 Using alternate debuggers
                        
              12. Kernel debugging examples
                 12.1 The case of the hung fsck
                 12.2 Episode 2: The case of the hung fsck
                        
              13. What to do when UML doesn't work
                 13.1 Strange compilation errors when you build from source
                 13.2 UML hangs on boot after mounting devfs
                 13.3 A variety of panics and hangs with /tmp on a reiserfs  filesystem
                 13.4 The compile fails with errors about conflicting types for 'open', 'dup', and 'waitpid'
                 13.5 UML doesn't work when /tmp is an NFS filesystem
                 13.6 UML hangs on boot when compiled with gprof support
                 13.7 syslogd dies with a SIGTERM on startup
                 13.8 TUN/TAP networking doesn't work on a 2.4 host
                 13.9 You can network to the host but not to other machines on the net
                 13.10 I have no root and I want to scream
                 13.11 UML build conflict between ptrace.h and ucontext.h
                 13.12 The UML BogoMips is exactly half the host's BogoMips
                 13.13 When you run UML, it immediately segfaults
                 13.14 xterms appear, then immediately disappear
                 13.15 cannot set up thread-local storage
                 13.16 Process segfaults with a modern (NPTL-using) filesystem
                 13.17 Any other panic, hang, or strange behavior
                        
              14. Diagnosing Problems
                 14.1 Case 1 : Normal kernel panics
                 14.2 Case 2 : Tracing thread panics
                 14.3 Case 3 : Tracing thread panics caused by other threads
                 14.4 Case 4 : Hangs
                        
              15. Thanks
                 15.1 Code and Documentation
                 15.2 Flushing out bugs
                 15.3 Buglets and clean-ups
                 15.4 Case Studies
                 15.5 Other contributions
                        
                        
              ______________________________________________________________________
                        
              [1m1.  Introduction[0m
                        
              Welcome to User Mode Linux.  It's going to be fun.
                        
                        
              [1m1.1.  What is User Mode Linux?[0m
                        
              User Mode Linux lets you run Linux inside itself! With that comes the
              power to do all sorts of new things. It virtualises (or simulates, as
            """;

    String cmucl = """
            ========================== C M U C L  20 a =============================
                        
            The CMUCL project is pleased to announce the release of CMUCL 20a.
            This is a major release which contains numerous enhancements and
            bug fixes from the 19f release.
                        
            CMUCL is a free, high performance implementation of the Common Lisp
            programming language which runs on most major Unix platforms. It
            mainly conforms to the ANSI Common Lisp standard. CMUCL provides a
            sophisticated native code compiler; a powerful foreign function
            interface; an implementation of CLOS, the Common Lisp Object System,
            which includes multi-methods and a meta-object protocol; a source-level
            debugger and code profiler; and an Emacs-like editor implemented in
            Common Lisp. CMUCL is maintained by a team of volunteers collaborating
            over the Internet, and is mostly in the public domain.
                        
            New in this release:
                        
              * Known issues:
                - On Linux and FreeBSD, it may not be possible call SAVE-LISP and
                  create executables.  This seems to be broken on FreeBSD.  On
                  Linux, it seems to depend on what version of Linux is used to
                  create the executable.  Redhat Enterprise Linux appears to be
                  ok, but Open SuSE 10.x is not.
            """;

    String xprint = """
            Archive-name: Xprint/FAQ_OLD
            Version: 0.8
            Last-Modified: 2003/08/04 15:20:19
            Maintained-by: Roland Mainz <Roland.Mainz@informatik.med.uni-giessen.de>
                        
            NOTE: This version of the FAQ has been discontinued and was replaced by the
            DocBook-based version available under xc/doc/hardcopy/XPRINT/Xprint_FAQ.xml
            (available through http from
            <http://xprint.mozdev.org/lxr/http/source/xprint/src/xprint_main/xc/doc/hardcopy/XPRINT/Xprint_FAQ.xml>)
                        
            The following is a list of questions that are frequently asked about
            Xprint.
                        
            You can help make it an even better-quality FAQ by writing a short
            contribution or update and sending it BY EMAIL ONLY to me.
            A contribution should consist of a question and an answer, and increasing
            number of people sends me contributions of the form "I don't know the
            answer to this, but it must be a FAQ, please answer it for me". Please
            read the FAQ first and then feel free to ask me if it is not in the FAQ.
                        
            Thanks!
            """;

    String vm = """
                        
            .. _vm:
                        
            =============================================================
            Clawpack Virtual Machine\s
            =============================================================
                        
            Using Clawpack requires a variety of other software packages, as summarized in
            :ref:`installing`. An alternative to installing the prerequisites is to use the
            virtual machine described in this section.
                        
            Another alternative is to run Clawpack on the Cloud, see :ref:`aws`.
                        
            To do so, you need only download and
            """;

    String garfinkel = """
            The Net Effect: The DVD Rebellion\s
            By Simson Garfinkel\s
            MIT Technology Review
            July/August 2001
                        
            Buy a copy of The Matrix on DVD and take it home.  Play it on a Mac or
            on a Windows PC and you're in for a pretty good time.  But play it on
            a PC running the Linux operating system, and the movie industry says
            that you're breaking the law.
                        
            Your transgression is that of "circumvention," a criminal act created
            by the 1998 Digital Millennium Copyright Act.  You see, the video on
            DVDs is scrambled.  Windows and Macintosh DVD players licensed by the
            DVD Copy Control Association contain the algorithms to unscramble the
            signal.  The Linux DVD player contains these secrets as well.  But
            since the Linux-based program isn't licensed, using the software
            constitutes an illegal circumvention of copyright management.
                        
            """;

    private final String PXE = """
                        
            PXE: Installing Slackware over the network
            ==========================================
                        
                        
            Introduction
            ------------
                        
                When the time comes to install Slackware on your computer, you have a\s
            limited number of options regarding the location of your Slackware\s
            packages.  Either you install them from the (un)official Slackware CDROM or\s
            DVD, or you copy them to a pre-existing hard disk partition before starting\s
            the installation procedure, or you fetch the packages from a network server
            (using either NFS, HTTP or FTP protocol).
                        
            """;

    private final String slackware = """
            Announcing Slackware Linux 7.1!
                        
            The first major release for 2000, Slackware Linux 7.1 builds on the
            success of Slackware 7.0.  In addition to program updates and distribution
            enhancements, you'll find the Konfucius (1.90) and the Kleopatra (1.91)
            developmental releases of the K Desktop Environment, XFree86 4.0,
            OpenMotif 2.1.30, and TrollTech's Qt 2.1.1 library available as system
            """;
    @Test
    void getDescription() {
        System.out.println(ptl.getDescription(LineUtils.firstNLines(PXE, 25)));
        System.out.println(ptl.getDescription(LineUtils.firstNLines(uml, 25)));
        System.out.println(ptl.getDescription(LineUtils.firstNLines(cmucl, 25)));
        System.out.println(ptl.getDescription(LineUtils.firstNLines(xprint, 25)));
        System.out.println(ptl.getDescription(LineUtils.firstNLines(vm, 25)));
        System.out.println(ptl.getDescription(LineUtils.firstNLines(garfinkel, 25)));
    }

    @Test
    void getTitle() throws URISyntaxException {
        System.out.println(ptl.getTitle(new EdgeUrl("http://mirror.cs.princeton.edu/pub/mirrors/slackware/slackware-7.1/ANNOUNCE.TXT"), LineUtils.firstNLines(slackware, 25)));
        System.out.println(ptl.getTitle(new EdgeUrl("https://slackjeff.com.br/slackware/slackware-14.2/usb-and-pxe-installers/README_PXE.TXT"), LineUtils.firstNLines(PXE, 25)));
        System.out.println(ptl.getTitle(new EdgeUrl("http://user-mode-linux.sourceforge.net/old/UserModeLinux-HOWTO.txt"), LineUtils.firstNLines(uml, 25)));
        System.out.println(ptl.getTitle(new EdgeUrl("https://www.cons.org/cmucl/news/release-20a.txt"), LineUtils.firstNLines(cmucl, 25)));
        System.out.println(ptl.getTitle(new EdgeUrl("https://www.x.org/docs/XPRINT/Xprint_old_FAQ.txt"), LineUtils.firstNLines(xprint, 25)));
        System.out.println(ptl.getTitle(new EdgeUrl("http://depts.washington.edu/clawpack/users-4.6/_sources/vm.txt"), LineUtils.firstNLines(vm, 25)));
        System.out.println(ptl.getTitle(new EdgeUrl("http://www.cs.cmu.edu/afs/cs.cmu.edu/user/dst/www/DeCSS/Gallery/archive/garfinkel.txt"), LineUtils.firstNLines(garfinkel, 25)));

    }
}