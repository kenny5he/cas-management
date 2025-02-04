package org.apereo.cas.mgmt.controller;

import org.apereo.cas.configuration.CasManagementConfigurationProperties;
import org.apereo.cas.mgmt.CommitStatus;
import org.apereo.cas.mgmt.GitUtil;
import org.apereo.cas.mgmt.authentication.CasUserProfile;
import org.apereo.cas.mgmt.domain.BranchActionData;
import org.apereo.cas.mgmt.domain.BranchData;
import org.apereo.cas.mgmt.exception.VersionControlException;
import org.apereo.cas.mgmt.factory.RepositoryFactory;
import org.apereo.cas.notifications.CommunicationsManager;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static java.util.stream.Collectors.*;

/**
 * Controller to handle pull requests.
 *
 * @author Travis Schmidt
 * @since 6.0
 */
@RestController("pullController")
@RequestMapping(path = "api/pull", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@RequiredArgsConstructor
public class PullController extends AbstractVersionControlController {

    private static final String NEW_LINE_INDENT = "\n   ";

    private final RepositoryFactory repositoryFactory;

    private final CasManagementConfigurationProperties managementProperties;

    private final CommunicationsManager communicationsManager;

    /**
     * Method will create a list of branches that have been submitted by users to be merged into the services-repo.
     *
     * @param authentication - the user
     * @param options        - List of Branch statuses filter the returned branches by
     * @return - List of BranchData
     * @throws VersionControlException - failed
     */
    @GetMapping
    public List<BranchData> branches(final Authentication authentication,
                                     @RequestParam
                                     final CommitStatus[] options) throws VersionControlException {
        try (GitUtil git = repositoryFactory.from(authentication)) {
            return git.branches()
                .map(git::mapBranches)
                .filter(r -> DelegatedUtil.filterPulls(r, EnumSet.copyOf(Arrays.asList(options))))
                .map(DelegatedUtil::createBranch)
                .collect(toList());
        } catch (final Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new VersionControlException();
        }
    }

    /**
     * Method will merge the submitted pull request into the services-repo.
     *
     * @param authentication - the user
     * @param acception      - BranchActionData
     * @throws VersionControlException - failed
     */
    @PostMapping(value = "/accept", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void acceptChange(final Authentication authentication,
                             @RequestBody
                             final BranchActionData acception) throws VersionControlException {
        val user = CasUserProfile.from(authentication);
        isAdministrator(user);
        val branch = acception.getBranch();
        val text = acception.getNote();
        try (GitUtil git = repositoryFactory.masterRepository()) {
            git.merge(branch.getId());
            val com = git.getCommit(branch.getId());
            val msg = "ACCEPTED by " + user.getId() + " on " + new Date() + NEW_LINE_INDENT
                      + text.replaceAll("\\n", NEW_LINE_INDENT);
            git.appendNote(com, msg);
            sendAcceptMessage(Iterables.get(Splitter.on('/').split(branch.getName()), 2), com.getCommitterIdent().getEmailAddress());
        } catch (final GitAPIException | IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new VersionControlException();
        }
    }

    /**
     * Method will mark the submitted pull request as being rejected by an admin.
     *
     * @param authentication - the user
     * @param rejection      - BranchActionData
     * @throws VersionControlException - failed
     */
    @PostMapping(value = "/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void rejectChange(final Authentication authentication,
                             @RequestBody
                             final BranchActionData rejection) throws VersionControlException {
        val user = CasUserProfile.from(authentication);
        isAdministrator(user);
        val branch = rejection.getBranch();
        val text = rejection.getNote();
        try (GitUtil git = repositoryFactory.masterRepository()) {
            val com = git.getCommit(branch.getId());
            val msg = "REJECTED by " + user.getId() + " on " + new Date() + NEW_LINE_INDENT
                      + text.replaceAll("\\n", NEW_LINE_INDENT);
            git.appendNote(com, msg);

            sendRejectMessage(Iterables.get(Splitter.on('/').split(branch.getName()), 2), text, com.getCommitterIdent().getEmailAddress());
        } catch (final GitAPIException | IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new VersionControlException();
        }
    }

    private void sendAcceptMessage(final String submitName, final String email) {
        if (communicationsManager.isMailSenderDefined()) {
            val emailProps = managementProperties.getDelegated().getNotifications().getAccept();
            emailProps.setSubject(MessageFormat.format(emailProps.getSubject(), submitName));
            communicationsManager.email(emailProps, email, MessageFormat.format(emailProps.getText(), submitName));
        }
    }

    private void sendRejectMessage(final String submitName, final String note, final String email) {
        if (communicationsManager.isMailSenderDefined()) {
            val emailProps = managementProperties.getDelegated().getNotifications().getReject();
            emailProps.setSubject(MessageFormat.format(emailProps.getSubject(), submitName));
            communicationsManager.email(emailProps, email, MessageFormat.format(emailProps.getText(), submitName, note));
        }
    }
}
