package org.carlspring.strongbox.validation.cron.autocomplete;

/**
 * @author Przemyslaw Fusik
 */
public interface CronTaskDefinitionFormFieldAutocompleteValidator
{

    boolean isValid(String value);

    boolean supports(String name);
}
