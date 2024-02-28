Stackexchange's data is a jumble of questions and answers,
where the answers refer to the questions with a parentId field.

e.g.
```xml
<?xml version="1.0" encoding="utf-8"?>
<posts>
  <row Id="1" PostTypeId="1" AcceptedAnswerId="51" CreationDate="2016-01-12T18:45:19.963" Score="10" ViewCount="424" Body="&lt;p&gt;When I've printed an object I've had to choose between high resolution and quick prints.  What techniques or technologies can I use or deploy to speed up my high resolution prints?&lt;/p&gt;&#xA;" OwnerUserId="16" LastActivityDate="2017-10-31T02:31:08.560" Title="How to obtain high resolution prints in a shorter period of time?" Tags="&lt;resolution&gt;&lt;speed&gt;&lt;quality&gt;" AnswerCount="2" CommentCount="6" ContentLicense="CC BY-SA 3.0" />
  <row Id="2" PostTypeId="1" AcceptedAnswerId="12" CreationDate="2016-01-12T18:45:51.287" Score="34" ViewCount="7377" Body="&lt;p&gt;I would like to buy a 3D printer, but I'm concerned about the health risks that are associated with its operation. Some groups of scientists say it can be &lt;a href=&quot;http://www.techworld.com/news/personal-tech/scientists-warn-of-3d-printing-health-effects-as-tech-hits-high-street-3460992/&quot;&gt;harmful&lt;/a&gt; for humans.&lt;/p&gt;&#xA;&#xA;&lt;p&gt;What do I need to consider before buying a 3D printer if I care about my health? Are there any safe printers?&lt;/p&gt;&#xA;" OwnerUserId="20" LastEditorUserId="334" LastEditDate="2016-11-15T16:16:11.163" LastActivityDate="2019-06-10T23:18:34.190" Title="Is 3D printing safe for your health?" Tags="&lt;print-material&gt;&lt;safety&gt;&lt;health&gt;" AnswerCount="4" CommentCount="1" ContentLicense="CC BY-SA 3.0" />
  <row Id="12" PostTypeId="2" ParentId="2" CreationDate="2016-01-12T19:13:00.710" Score="23" Body="&lt;p&gt;There is very little information about safety available, as home 3D printers are relatively new. However, plastics such as ABS have a long history in making plastic products, and a study found..." />
</posts>
```

Since the search engine wants to extract keywords for each thread
holistically, not by question or answer, it is necessary to re-arrange
the data (which is very large).  SQLite does a decent job of enabling
this task.

