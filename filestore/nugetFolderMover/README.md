Artifactory NuGet Folder Mover User Plugin
==========================================

*This plugin is currently only working in Artifactory 4.x. We are working on updating it to work in Artifactory 5.x.*

This plugin converts the NuGet packages under the root of the repository, as in
the following example:

`nuget-local/NHibernate.3.3.1.4000.nupkg` &rarr;
`nuget-local/NHibernate/NHibernate/NHibernate.3.3.1.4000.nupkg`

This plugin has a cron job scheduled to run every five minutes.
