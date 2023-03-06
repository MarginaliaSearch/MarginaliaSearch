#!/usr/bin/env python3
import markdown
import bs4
import git
import os

git_root = '../..'
git = git.Git(git_root)

def is_local(url):
    return ":" not in url

def local_links(file_name):
    with open(file_name) as file_handle:
        html = markdown.markdown(file_handle.read(), output_format='html')
        soup = bs4.BeautifulSoup(html, 'html.parser')
        hrefs = [ link['href'] for link in soup.find_all('a') ]
        return [ href for href in hrefs if is_local(href) ]


def lint_links():
  files = git.ls_files('*.md').split('\n')

  for file_name in [git_root + '/' + file for file in files ]:
    dir_name=os.path.dirname(file_name)
    referenced_file = [ os.path.join(dir_name, link) for link in local_links(file_name)]
    for link_dest in referenced_file:
        if not os.path.exists(link_dest):
            rel_file_name = os.path.relpath(file_name, git_root)
            rel_link_dest = os.path.relpath(link_dest, git_root)
            print("!!! {} is referencing missing file {}".format(rel_file_name, rel_link_dest))

def lint_projects():
  files = git.ls_files('*/build.gradle').split('\n')

  for file_name in [git_root + '/' + file for file in files ]:
    dir_name=os.path.dirname(file_name)
    expected_readme = os.path.join(dir_name, 'readme.md')
    expected_readme2 = os.path.join(dir_name, 'README.md')
    if not os.path.exists(expected_readme) and not os.path.exists(expected_readme2):
      rel_file_name = os.path.relpath(expected_readme, git_root)
      print("!!! Expected readme.md in {}".format(rel_file_name))


lint_links()
lint_projects()
