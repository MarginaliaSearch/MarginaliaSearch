@import nu.marginalia.language.model.LanguageDefinition
@import nu.marginalia.search.model.DecoratedSearchResults
@import nu.marginalia.search.model.NavbarModel
@import java.util.Map

@param DecoratedSearchResults results
@param NavbarModel navbar
@param String requestMethod
@param String displayUrl
@param Map<String, LanguageDefinition> languageDefinitions

<!DOCTYPE html>
<html lang="en">
<body bgcolor="black"> <!-- avoid flashbang with 1993 HTML -->
<div style="visibility: hidden">
    @template.serp.part.searchform(query = results.getParams().query(),
                                   profile = results.getProfile(),
                                   filters = results.getFilters(),
                                   languageIsoCode = results.getLanguageIsoCode(),
                                   page = results.page(),
                                   requestMethod = requestMethod,
                                   displayUrl = displayUrl)
</div>
<script lang="javascript">
    // auto-fire the form to convert our GET request to a POST request
    document.getElementById('search-form').submit()
</script>
</body>
</html>
</html>