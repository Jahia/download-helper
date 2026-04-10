import {DocumentNode} from 'graphql';

describe('Download Helper', () => {
    const adminPath = '/jahia/administration/downloadHelper';

    let getDownloadHelperInfo: DocumentNode;
    let getDownloadHelperFiles: DocumentNode;
    let triggerDownload: DocumentNode;
    let deleteDownloadedFile: DocumentNode;

    getDownloadHelperInfo = require('graphql-tag/loader!../fixtures/graphql/query/getDownloadHelperInfo.graphql');
    getDownloadHelperFiles = require('graphql-tag/loader!../fixtures/graphql/query/getDownloadHelperFiles.graphql');
    triggerDownload = require('graphql-tag/loader!../fixtures/graphql/mutation/triggerDownload.graphql');
    deleteDownloadedFile = require('graphql-tag/loader!../fixtures/graphql/mutation/deleteDownloadedFile.graphql');

    before(() => {
        cy.login();
    });

    it('returns server info via GraphQL', () => {
        cy.apollo({query: getDownloadHelperInfo})
            .its('data.downloadHelperInfo')
            .should(info => {
                expect(info).to.have.property('isProcessingServer');
                expect(info).to.have.property('availableSpace');
                expect(info).to.have.property('downloadFolderPath');
                expect(info).to.have.property('isMailActivated');
            });
    });

    it('shows the admin panel with all form fields', () => {
        cy.login();
        cy.visit(adminPath);

        cy.get('#dh-protocol').should('be.visible');
        cy.get('#dh-url').should('be.visible');
        cy.get('#dh-filename').should('be.visible');
        cy.get('#dh-login').should('be.visible');
        cy.get('#dh-password').should('be.visible');
        cy.get('#dh-email').should('be.visible');
    });

    it('disables the trigger button when URL or filename is empty', () => {
        cy.login();
        cy.visit(adminPath);

        // Both empty — button disabled
        cy.contains('button', 'Start download').should('be.disabled');

        // URL set but filename cleared — still disabled
        cy.get('#dh-url input').type('example.com/file.zip');
        cy.get('#dh-filename input').clear();
        cy.contains('button', 'Start download').should('be.disabled');
    });

    it('auto-populates filename when a URL is typed', () => {
        cy.login();
        cy.visit(adminPath);

        cy.get('#dh-url input').type('example.com/path/to/archive.tar.gz');
        cy.get('#dh-filename input').should('have.value', 'archive.tar.gz');
    });

    it('auto-detects protocol from a pasted URL and strips it', () => {
        cy.login();
        cy.visit(adminPath);
        
        // Paste an https URL — protocol selector switches and prefix is stripped
        cy.get('#dh-url input').type('https://example.com/file.zip');
        cy.get('#dh-url input').should('have.value', 'example.com/file.zip');
        cy.get('#dh-protocol select').should('have.value', 'https');
        
        // Clear and paste an ftp URL
        cy.get('#dh-url input').clear().type('ftp://files.example.com/data.tar.gz');
        cy.get('#dh-url input').should('have.value', 'files.example.com/data.tar.gz');
        cy.get('#dh-protocol select').should('have.value', 'ftp');
    });

    it('keeps manually entered filename when URL changes', () => {
        cy.login();
        cy.visit(adminPath);

        cy.get('#dh-url input').type('example.com/path/to/archive.zip');
        cy.get('#dh-filename input').should('have.value', 'archive.zip');

        // Manually override the filename
        cy.get('#dh-filename input').clear().type('my-custom-name.zip');

        // Changing the URL should NOT overwrite the manually set filename
        cy.get('#dh-url input').clear().type('example.com/other/file.tar.gz');
        cy.get('#dh-filename input').should('have.value', 'my-custom-name.zip');
    });

    it('enables the trigger button when URL and filename are both set', () => {
        cy.login();
        cy.visit(adminPath);

        cy.get('#dh-url input').type('example.com/file.zip');
        cy.get('#dh-filename input').should('have.value', 'file.zip');
        cy.contains('button', 'Start download').should('not.be.disabled');
    });

    it('returns an empty file list via GraphQL when no files are present', () => {
        cy.apollo({query: getDownloadHelperFiles})
            .its('data.downloadHelperFiles')
            .should('be.an', 'array');
    });

    it('triggers a download via GraphQL mutation and returns true', () => {
        cy.apollo({
            mutation: triggerDownload,
            variables: {
                protocol: 'https',
                url: 'store.jahia.com/cms/mavenproxy/private-app-store/org/jahia/modules/addstuff/3.0.0/addstuff-3.0.0.jar',
                filename: 'addstuff-3.0.0.jar',
                login: null,
                password: null,
                email: null
            }
        })
            .its('data.downloadHelperTrigger')
            .should('eq', true);
        cy.apollo({
            mutation: deleteDownloadedFile,
            variables: {filename: 'addstuff-3.0.0.jar'}
        })
    });

    it('triggers a download via the interface and checks the downloaded file', () => {
        cy.login();
        cy.visit(adminPath);
        cy.get('input[placeholder="example.com/path/to/file"]').click();
        cy.get('input[placeholder="example.com/path/to/file"]').type('store.jahia.com/cms/mavenproxy/private-app-store/org/jahia/modules/addstuff/3.0.0/addstuff-3.0.0.jar');
        cy.get('#reactComponent button.moonstone-button_primary span.flexFluid').click();
        cy.contains('button', 'Refresh').should('be.visible');
        cy.get('#reactComponent span.moonstone-weight_light').click();
        cy.contains('addstuff-3.0.0.jar');
    });

    it('deletes a non-existent file via GraphQL and returns false', () => {
        cy.apollo({
            mutation: deleteDownloadedFile,
            variables: {filename: 'does-not-exist.txt'}
        })
            .its('data.downloadHelperDeleteFile')
            .should('eq', false);
    });

    it('rejects path traversal filenames via GraphQL', () => {
        cy.apollo({
            mutation: deleteDownloadedFile,
            variables: {filename: '../../../etc/passwd'}
        })
            .its('data.downloadHelperDeleteFile')
            .should('eq', false);
    });

    it('shows the downloaded files section in the UI', () => {
        cy.login();
        cy.visit(adminPath);

        cy.contains('Downloaded files').should('be.visible');
        cy.contains('button', 'Refresh').should('be.visible');
    });
});
