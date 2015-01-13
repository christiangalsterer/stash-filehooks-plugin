# Introduction
An Atlassian Stash plugin to check on various file attributes, like size, name.

# Installation
The plugin is available in the [Atlassian Marketplace](https://marketplace.atlassian.com/) and can be installed directly in Stash using the Universal Plugin Manager (UPM).

# Configuration
## File Size Hook
In order to configure the hook go to your repository and select **Settings** -> **Hooks** -> **File Size Hook**.
The following example rejects all files larger then 1MB (1048576 bytes).

![File Size Hook Configuration](screenshots/filesize-hook-config.png)

# Releases

1.0.0 (2015-01-XX)

* Reject commits containing files which exceed a configurable file size. Files can be identified by regular expressions.

# Roadmap
* Reject commits containing files which match a regular expression.
* Allow different file size limits for different files.

# License

```
   Copyright 2015 Christian Galsterer

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```