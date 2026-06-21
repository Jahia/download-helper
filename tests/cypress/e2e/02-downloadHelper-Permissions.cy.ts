import {DocumentNode} from 'graphql';
import {createUser, deleteUser, grantRoles} from '@jahia/cypress';

/**
 * Regression tests for the fine-grained `adminDownloadHelper` permission.
 *
 * These guard against the gate being silently removed or mismatched across the stack:
 *  - Backend: `@GraphQLRequiresPermission("adminDownloadHelper")` is enforced as
 *    `session.getNode("/").hasPermission("adminDownloadHelper")` (root-node ACL check).
 *  - Frontend: `requiredPermission: 'adminDownloadHelper'` in register.jsx gates the admin route.
 *  - RBAC content: the module ships the assignable `download-helper-administrator` role
 *    (src/main/import/roles.xml) granting ONLY `administrationAccess adminDownloadHelper`.
 *
 * The "allowed" user is granted that role and nothing else — never `admin` — so the tests prove
 * fine-grained granularity, not merely that a full administrator can pass.
 */
describe('Download Helper — permission enforcement', () => {
    const ROLE_NAME = 'download-helper-administrator';
    const DENIED_USER = 'dhDeniedUser';
    const ALLOWED_USER = 'dhAllowedUser';
    const PASSWORD = 'DhPerm9PwdTest';
    const ADMIN_PATH = '/jahia/administration/downloadHelper';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getDownloadHelperInfo: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getDownloadHelperInfo.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getDownloadHelperFiles: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getDownloadHelperFiles.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const triggerDownload: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/triggerDownload.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const deleteDownloadedFile: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/deleteDownloadedFile.graphql');

    const errorsOf = (result: {graphQLErrors?: Array<{message: string}>; errors?: Array<{message: string}>}) =>
        result.graphQLErrors ?? result.errors ?? [];

    const queryInfoAs = (username: string) => {
        cy.apolloClient({username, password: PASSWORD});
        return cy.apollo({query: getDownloadHelperInfo});
    };

    const denyAs = (username: string, op: Record<string, unknown>) => {
        cy.apolloClient({username, password: PASSWORD});
        return cy.apollo(op);
    };

    before(() => {
        cy.login();
        createUser(DENIED_USER, PASSWORD);
        createUser(ALLOWED_USER, PASSWORD);
        // The annotation resolves the permission on the JCR root node, so grant the
        // module-shipped single-permission role on `/`.
        grantRoles('/', [ROLE_NAME], ALLOWED_USER, 'USER');
    });

    after(() => {
        cy.apolloClient(); // reset the current Apollo client back to root
        cy.login();
        deleteUser(DENIED_USER);
        deleteUser(ALLOWED_USER);
    });

    describe('GraphQL API authorization', () => {
        it('denies the gated query for a user without the permission', () => {
            queryInfoAs(DENIED_USER).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });

        it('allows the gated query for a user granted only the module permission', () => {
            queryInfoAs(ALLOWED_USER).then((result: never) => {
                expect(errorsOf(result), 'should have no errors').to.have.length(0);
                const info = (result as {data: {downloadHelper: {info: {downloadFolderPath: string}}}}).data.downloadHelper.info;
                expect(info).to.have.property('isProcessingServer');
                expect(info).to.have.property('availableSpace');
                expect(info).to.have.property('downloadFolderPath');
                expect(info).to.have.property('isMailActivated');
            });
        });

        it('denies the downloadHelperFiles query for a user without the permission', () => {
            denyAs(DENIED_USER, {query: getDownloadHelperFiles}).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });

        it('denies the downloadHelperTrigger mutation for a user without the permission', () => {
            denyAs(DENIED_USER, {
                mutation: triggerDownload,
                variables: {
                    protocol: 'https',
                    url: 'example.com/file.zip',
                    filename: 'file.zip',
                    login: null,
                    password: null,
                    email: null
                }
            }).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });

        it('denies the downloadHelperDeleteFile mutation for a user without the permission', () => {
            denyAs(DENIED_USER, {
                mutation: deleteDownloadedFile,
                variables: {filename: 'whatever.txt'}
            }).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });
    });

    describe('Admin UI authorization', () => {
        it('hides the admin panel from a user without the permission', () => {
            cy.login(DENIED_USER, PASSWORD);
            cy.visit(ADMIN_PATH, {failOnStatusCode: false});
            cy.get('#dh-url').should('not.exist');
        });

        it('shows the admin panel to a user granted only the module permission', () => {
            cy.login(ALLOWED_USER, PASSWORD);
            cy.visit(ADMIN_PATH);
            cy.get('#dh-url').should('be.visible');
        });
    });
});
