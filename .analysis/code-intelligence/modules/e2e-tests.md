# Module Analysis — e2e-tests

**Last Updated:** 2026-04-30T10:44:27.011Z
**Language:** javascript | **Framework:** —

## Package Structure

```
e2e-tests/
├── com.assistant.e2e.api/     # HTTP request handling
├── com.assistant.e2e/     # Application logic
├── com.assistant.e2e.runners/     # Application logic
└── com.assistant.e2e.steps/     # Application logic
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| AnalysisApiTest | com.assistant.e2e.api | Test class | public |
| for | com.assistant.e2e.api | Application component | public |
| ApiTestBase | com.assistant.e2e.api | Application component | abstract |
| AttachmentApiTest | com.assistant.e2e.api | Test class | public |
| AuthApiTest | com.assistant.e2e.api | Test class | public |
| ChatApiTest | com.assistant.e2e.api | Test class | public |
| DocumentJobManagerApiTest | com.assistant.e2e.api | Test class | public |
| ErrorHandlingApiTest | com.assistant.e2e.api | Test class | public |
| names | com.assistant.e2e.api | Application component | public |
| EstimationApiTest | com.assistant.e2e.api | Test class | public |
| GraphApiTest | com.assistant.e2e.api | Test class | public |
| HealthApiTest | com.assistant.e2e.api | Test class | public |
| IntegrationsApiTest | com.assistant.e2e.api | Test class | public |
| McpInternalApiTest | com.assistant.e2e.api | Test class | public |
| MultiProjectScanApiTest | com.assistant.e2e.api | Test class | public |
| RbacApiTest | com.assistant.e2e.api | Test class | public |
| RegressionApiTest | com.assistant.e2e.api | Test class | public |
| ScanApiTest | com.assistant.e2e.api | Test class | public |
| SettingsApiTest | com.assistant.e2e.api | Test class | public |
| TicketStatusApiTest | com.assistant.e2e.api | Test class | public |
| UserManagementApiTest | com.assistant.e2e.api | Test class | public |
| ApiTestRunner | com.assistant.e2e.api | Application component | public |
| CucumberTestRunner | com.assistant.e2e.api | Application component | public |
| UiAIChatSidebarRunner | com.assistant.e2e.api | Application component | public |
| UiAppSettingsRunner | com.assistant.e2e.api | Application component | public |
| UiBatchScanRunner | com.assistant.e2e.api | Application component | public |
| UiDashboardRunner | com.assistant.e2e.api | Application component | public |
| UiDocumentJobManagerRunner | com.assistant.e2e.api | Application component | public |
| UiFirstLaunchRunner | com.assistant.e2e.api | Application component | public |
| UiFrontendBackendRunner | com.assistant.e2e.api | Application component | public |
| UiInitializationRunner | com.assistant.e2e.api | Application component | public |
| UiIntegrationsRunner | com.assistant.e2e.api | Application component | public |
| UiKnowledgeGraphRunner | com.assistant.e2e.api | Application component | public |
| UiMcpServersRunner | com.assistant.e2e.api | Application component | public |
| UiSecurityRunner | com.assistant.e2e.api | Application component | public |
| UiTicketIntelligenceRunner | com.assistant.e2e.api | Application component | public |
| UiUserManagementRunner | com.assistant.e2e.api | Application component | public |
| AIAnalysisSteps | com.assistant.e2e.api | Application component | public |
| AIChatSidebarSteps | com.assistant.e2e.api | Application component | public |
| AppSettingsSteps | com.assistant.e2e.api | Application component | public |
| BatchScanSteps | com.assistant.e2e.api | Application component | public |
| CommonSteps | com.assistant.e2e.api | Application component | public |
| DashboardSteps | com.assistant.e2e.api | Application component | public |
| DocumentJobManagerSteps | com.assistant.e2e.api | Application component | public |
| EstimationSteps | com.assistant.e2e.api | Application component | public |
| FirstLaunchRedirectSteps | com.assistant.e2e.api | Application component | public |
| FrontendBackendIntegrationSteps | com.assistant.e2e.api | Application component | public |
| InitializationSteps | com.assistant.e2e.api | Application component | public |
| IntegrationsSteps | com.assistant.e2e.api | Application component | public |
| KnowledgeGraphSteps | com.assistant.e2e.api | Application component | public |
| McpServersSteps | com.assistant.e2e.api | Application component | public |
| SecuritySteps | com.assistant.e2e.api | Application component | public |
| per | com.assistant.e2e.api | Application component | public |
| SharedTestContext | com.assistant.e2e.api | Application component | public |
| TestApiClient | com.assistant.e2e.api | External service client | public |
| TestHelper | com.assistant.e2e.api | Utility functions | public |
| TicketIntelligenceSteps | com.assistant.e2e.api | Application component | public |
| check | com.assistant.e2e.api | Application component | public |
| UserManagementSteps | com.assistant.e2e.api | Application component | public |

## Public API Surface

- `analysisRequiresAuth(): Unit`
- `analysisWithAuth(): Unit`
- `projectAnalysisReturnsExpectedFields(): Unit`
- `projectAnalysisRequiresAuth(): Unit`
- `projectIssuesEndpoint(): Unit`
- `projectIssuesRequiresAuth(): Unit`
- `analysisStatusEndpoint(): Unit`
- `analysisStatusRequiresAuth(): Unit`
- `reanalyzeEndpoint(): Unit`
- `reanalyzeRequiresAuth(): Unit`
- `analysisResultIsCachedInKB(): Unit`
- `readerJwt(): Unit`
- `neuralArchitectJwt(): Unit`
- `setupTestEnvironment(): Unit`
- `assumeJiraConfigured(): Unit`
- `getAttachmentStatusReturns200(): Unit`
- `getAttachmentStatusForNonExistentTicketReturnsEmptyList(): Unit`
- `readerCanViewAttachmentStatus(): Unit`
- `unauthenticatedRequestReturns401(): Unit`
- `attachmentStatusContainsExpectedFieldsWhenDataExists(): Unit`
- `loginReturnsJwtToken(): Unit`
- `jwtContainsRequiredClaims(): Unit`
- `authenticatedRequestSucceeds(): Unit`
- `authenticatedTestAuthEndpoint(): Unit`
- `unauthenticatedRequestReturns401(): Unit`
- `malformedJwtReturns401(): Unit`
- `expiredJwtReturns401(): Unit`
- `emptyBearerTokenReturns401(): Unit`
- `noBearerPrefixReturns401(): Unit`
- `loginWithMalformedJsonReturns400or500(): Unit`
- `logoutWithoutAuthReturns401(): Unit`
- `doubleLoginBothSucceed(): Unit`
- `doubleLogoutSecondReturns401(): Unit`
- `logoutInvalidatesSession(): Unit`
- `jwtHas24HourExpiry(): Unit`
- `jwtHasIssuerClaim(): Unit`
- `loginResponseContainsUserObject(): Unit`
- `sendChatReturns200WithReply(): Unit`
- `sendChatSavesToHistory(): Unit`
- `executeActionNavigateReturns200(): Unit`
- `executeActionChangeConfigReaderReturns403(): Unit`
- `executeActionChangeConfigAdminReturns200(): Unit`
- `executeActionTriggerAnalysisReaderReturns403(): Unit`
- `getHistoryReturns200WithPagination(): Unit`
- `getHistoryEmptyReturnsEmptyList(): Unit`
- `deleteHistoryReturns200ThenGetReturnsEmpty(): Unit`
- `noJwtReturns401(): Unit`
- `nonExistentApiRouteReturns404or401(): Unit`
- `analysisNonExistentTicket(): Unit`
- `errorResponseIsJson(): Unit`
- `errorResponseHasErrorField(): Unit`
- `noStackTraceInAnyErrorResponse(): Unit`
- `healthResponseFormat(): Unit`
- `allApiGroupsExist(): Unit`
- `invalidJsonBodyReturns400or500(): Unit`
- `estimationRequiresAuth(): Unit`
- `estimationWithAuthReturnsResult(): Unit`
- `estimationResponseContainsExpectedFields(): Unit`
- `estimationResponseIsJson(): Unit`
- `estimationReturnsValidScrumPoint(): Unit`
- `estimationEmptySummaryReturns400(): Unit`
- `estimationMalformedJsonReturns400or500(): Unit`
- `estimationWithHistoricalTickets(): Unit`
- `estimationWithFeatureArea(): Unit`
- `readerCannotEstimate(): Unit`
- `neuralArchitectCanEstimate(): Unit`
- `estimationErrorNoStackTrace(): Unit`
- `graphRequiresAuth(): Unit`
- `graphWithAuth(): Unit`
- `graphResponseContainsExpectedFields(): Unit`
- `graphResponseIsJson(): Unit`
- `graphSearchFilterAccepted(): Unit`
- `graphEmptySearchReturnsAll(): Unit`
- `graphNonExistentProjectReturns404(): Unit`
- `graphMissingProjectKeyReturns400(): Unit`
- `readerCanAccessGraph(): Unit`
- `neuralArchitectCanAccessGraph(): Unit`
- `graphErrorResponseNoStackTrace(): Unit`
- `healthEndpointReturns200(): Unit`
- `settingsStatusIsPublic(): Unit`
- `jiraStatusIsPublic(): Unit`
- `getIntegrationsRequiresAuth(): Unit`
- `getIntegrationsWithAuth(): Unit`
- `jiraConfigSaveAndTest(): Unit`
- `providerConfigUpdate(): Unit`
- `jiraConfigMissingFields(): Unit`
- `jiraConfigMissingEmail(): Unit`
- `testNonExistentProvider(): Unit`
- `configNonExistentProvider(): Unit`
- `jiraStatusReturnsFalseWhenNotConfigured(): Unit`
- `jiraStatusReturnsTrueAfterConfig(): Unit`
- `testProviderRequiresAuth(): Unit`
- `configProviderRequiresAuth(): Unit`
- `jiraStatusResponseFormat(): Unit`
- `realJiraConfigReturnsActiveStatus(): Unit`
- `realJiraProjectsReturnNonEmptyList(): Unit`
- `realJiraProjectIssuesReturnData(): Unit`
- `realJiraProjectAnalysisReturnsMetrics(): Unit`
- `listMcpServersRequiresAuth(): Unit`
- `listMcpServersWithAuth(): Unit`
- `internalServerAlwaysPresent(): Unit`
- `readerCanListMcpServers(): Unit`
- `cannotDeleteInternalServer(): Unit`
- `cannotStopInternalServer(): Unit`
- `cannotDisableInternalServerViaUpdate(): Unit`
- `internalServerStatusAlwaysRunning(): Unit`
- `internalServerStatusContainsToolCount(): Unit`
- `readerCanViewInternalServerStatus(): Unit`
- `getInternalServerTools(): Unit`
- `toolsHaveInputSchema(): Unit`
- `aggregatedToolsIncludeInternal(): Unit`
- `readerCanViewTools(): Unit`
- `aggregatedToolsReaderAccess(): Unit`
- `executeNavigateToPage(): Unit`
- `executeListAvailablePages(): Unit`
- `executeGetCurrentPage(): Unit`
- `navigateToPageInvalidEnum(): Unit`
- `executeGetScanStatus(): Unit`
- `executeGetScanLog(): Unit`
- `startScanMissingProjectKey(): Unit`
- `executeGetTicketAnalysis(): Unit`
- `executeListAnalyzedTickets(): Unit`
- `executeListConversations(): Unit`
- `executeGetChatHistory(): Unit`
- `executeGetSettings(): Unit`
- `executeGetSetting(): Unit`
- `executeListUsers(): Unit`
- `executeListAiProviders(): Unit`
- `executeListMcpServers(): Unit`
- `manageMcpServerSelfProtection(): Unit`
- `executeGetGraphData(): Unit`
- `executeSearchGraphNodes(): Unit`
- `executeGetDashboardMetrics(): Unit`
- `executeListProjects(): Unit`
- `executeGetProjectAnalysisSummary(): Unit`
- `readerCanCallReadOnlyTools(): Unit`
- `readerCannotCallWriteTools(): Unit`
- `neuralArchitectCanCallAnalyzeTools(): Unit`
- `neuralArchitectCannotManageUsers(): Unit`
- `toolCallRequiresAuth(): Unit`
- `missingRequiredArgument(): Unit`
- `unknownToolReturnsError(): Unit`
- `invalidEnumArgument(): Unit`
- `businessErrorTicketNotFound(): Unit`
- `businessErrorProjectNotConfigured(): Unit`
- `createMcpServer(): Unit`
- `duplicateNameReturns409(): Unit`
- `readerCannotCreateMcpServer(): Unit`
- `readerCannotDeleteMcpServer(): Unit`
- `exportMcpConfig(): Unit`
- `importMcpConfigSkipsDuplicates(): Unit`
- `testInternalServerConnection(): Unit`
- `testConnectionRequiresAdmin(): Unit`
- `getServerLogsRequiresAdmin(): Unit`
- `getServerLogsWithAdmin(): Unit`
- `getActiveScanReturns200WithList(): Unit`
- `readerCanViewActiveScans(): Unit`
- `getActiveScanWithoutJwtReturns401(): Unit`
- `startScanConflictReturns409WithMessage(): Unit`
- `getActiveScanResponseFormatIsCorrect(): Unit`
- `readerCannotAccessSettings(): Unit`
- `readerCannotUpdateSettings(): Unit`
- `readerCannotConfigureIntegrations(): Unit`
- `readerCannotManageUsers(): Unit`
- `readerCanViewIntegrations(): Unit`
- `readerCanViewProjects(): Unit`
- `readerCannotAnalyze(): Unit`
- `readerCannotReanalyze(): Unit`
- `neuralArchitectCanAnalyze(): Unit`
- `testProviderRequiresAdminRole(): Unit`
- `jiraConfigRequiresAdminRole(): Unit`
- `changeUserRoleRequiresAdmin(): Unit`
- `neuralArchitectCannotManageUsers(): Unit`
- `estimationReaderDenied(): Unit`
- `estimationNeuralArchitectAllowed(): Unit`
- `neuralArchitectCanViewGraph(): Unit`
- `readerCanViewGraph(): Unit`
- `readerCannotTestProvider(): Unit`
- `neuralArchitectCannotTestProvider(): Unit`
- `loginWithWrongPasswordReturns401(): Unit`
- `loginWithNonExistentUserReturns401(): Unit`
- `loginWithEmptyCredentialsReturns401(): Unit`
- `loginAsUserReturnsReaderRole(): Unit`
- `loginAsAdminReturnsAdministratorRole(): Unit`
- `integrationsReturnsSavedProvidersFromDb(): Unit`
- `jiraTestLinkReturnsValidJson(): Unit`
- `jiraTestLinkWithoutConfigReturnsOffline(): Unit`
- `jiraConfigResponseHasProjectObjects(): Unit`
- `jiraStatusPersistsAfterConfig(): Unit`
- `unauthorizedDoesNotCauseRedirectLoop(): Unit`
- `providerConfigUpdateReturnsValidJson(): Unit`
- `projectsEndpointReturnsDataAfterJiraConfig(): Unit`
- `rbacMiddlewareWorksCorrectly(): Unit`
- `startScanReturns200WithScanningStatus(): Unit`
- `startScanWhileAlreadyScanningReturns409OrCompletesInstantly(): Unit`
- `pauseScanReturns200WithPausedStatus(): Unit`
- `resumeScanReturns200WithScanningStatus(): Unit`
- `cancelScanReturns200WithCancelledStatus(): Unit`
- `getStatusReturns200WithScanStatusResponse(): Unit`
- `getLogReturns200WithScanLogResponse(): Unit`
- `readerCannotStartScan(): Unit`
- `readerCanViewScanStatus(): Unit`
- `getSettingsRequiresAuth(): Unit`
- `getSettingsWithAuth(): Unit`
- `putSettingsIgnoresUnknownFields(): Unit`
- `putSettingsSuccess(): Unit`
- `settingsInvalidPort(): Unit`
- `settingsIgnoresLegacyFields(): Unit`
- `settingsEmptyBodyReturns400or200(): Unit`
- `settingsStatusReturnsFalseWhenNotConfigured(): Unit`
- `getTicketStatusReturns200(): Unit`
- `getTicketStatusReturnsJsonArray(): Unit`
- `ticketStatusEntriesHaveRequiredFields(): Unit`
- `ticketStatusAnalysisStateIsValid(): Unit`
- `ticketStatusDefaultStateIsNotAnalyzed(): Unit`
- `readerCanViewTicketStatus(): Unit`
- `unauthenticatedTicketStatusReturns401(): Unit`
- `getUsersRequiresAuth(): Unit`
- `getUsersWithAuth(): Unit`
- `changeUserRoleEndpoint(): Unit`
- `changeUserRoleRequiresAuth(): Unit`
- `togglePermissionEndpoint(): Unit`
- `togglePermissionRequiresAuth(): Unit`
- `ticketHasCachedAnalysis(ticketId: String): Unit`
- `ticketHasNoCachedAnalysis(ticketId: String): Unit`
- `primaryProviderOffline(): Unit`
- `secondaryProviderActive(): Unit`
- `projectHasTickets(project: String, ticket1: String, ticket2: String): Unit`
- `aiProviderReturnsInvalidJson(): Unit`
- `orchestratorReceivesValidResponse(): Unit`
- `orchestratorProcessesResponse(): Unit`
- `responseReturnsCachedResult(): Unit`
- `noAiProviderCalled(): Unit`
- `responseContainsAnalysisFields(): Unit`
- `orchestratorCallsHighestPriority(): Unit`
- `resultSavedToKb(): Unit`
- `responseContainsAnalysisResult(): Unit`
- `orchestratorCallsRegardlessOfCache(): Unit`
- `kbRecordOverwritten(): Unit`
- `orchestratorFailsOverToGemini(): Unit`
- `failoverEventLogged(): Unit`
- `responseContainsNodes(): Unit`
- `responseContainsEdges(): Unit`
- `semanticRelationshipsMarked(type: String): Unit`
- `parseResponseIntoStructuredResult(): Unit`
- `scrumPointWithinValidScale(): Unit`
- `retryUpTo2Times(): Unit`
- `retriesFailReturnError(): Unit`
- `toggleButtonVisible(): Unit`
- `userClicksToggleButton(): Unit`
- `sidebarShouldBeOpen(): Unit`
- `sidebarDisplaysHeader(headerText: String): Unit`
- `sidebarDisplaysCloseButton(): Unit`
- `userClicksCloseButton(): Unit`
- `sidebarShouldBeClosed(): Unit`
- `sidebarDisplaysChatInput(): Unit`
- `sidebarDisplaysSendButton(): Unit`
- `userTypesInChatInput(message: String): Unit`
- `userPressesEnterInChatInput(): Unit`
- `userSendsChatMessage(message: String): Unit`
- `userMessageBubbleAppears(): Unit`
- `assistantMessageBubbleAppears(): Unit`
- `typingIndicatorAppearsBriefly(): Unit`
- `actionButtonsAppear(): Unit`
- `userClicksChatActionButton(): Unit`
- `browserHashContains(expected: String): Unit`
- `aiChatEncountersProviderError(): Unit`
- `chatErrorBannerVisible(): Unit`
- `errorBannerContainsIntegrationsLink(): Unit`
- `userPressesArrowUpInChatInput(): Unit`
- `chatInputContainsPreviousMessage(): Unit`
- `chatMessageAreaPresent(): Unit`
- `userTriggersChangeConfigAction(): Unit`
- `permissionDeniedMessageAppears(): Unit`
- `userOpensLoginPage(): Unit`
- `userOpensProjectSelectPage(): Unit`
- `toggleButtonNotVisible(): Unit`
- `userNavigatesToSettings(): Unit`
- `settingsPageDisplayed(): Unit`
- `pageDisplaysAllFields(): Unit`
- `portReadonlyWithBadge(badge: String): Unit`
- `jwtSecretIsMasked(): Unit`
- `encryptionKeyIsMasked(): Unit`
- `jwtSecretShowsLast4(): Unit`
- `encryptionKeyShowsLast4(): Unit`
- `userUpdatesJiraHost(value: String): Unit`
- `userClicksSaveSettingsButton(): Unit`
- `successMessageAppears(message: String): Unit`
- `backendPersistsSettings(): Unit`
- `portInputDisabled(): Unit`
- `userEntersInJiraHost(value: String): Unit`
- `backendReturnsError(status: Int): Unit`
- `settingsFormNotVisible(): Unit`
- `dbContainsSetting(value: String): Unit`
- `envVariableSet(value: String): Unit`
- `backendLoadsConfig(): Unit`
- `effectiveJiraHostIs(expected: String): Unit`
- `scanControlPanelVisible(): Unit`
- `startScanButtonVisible(): Unit`
- `userClicksStartScan(): Unit`
- `scanStatusScanning(): Unit`
- `pauseButtonVisible(): Unit`
- `userClicksPause(): Unit`
- `scanStatusPaused(): Unit`
- `resumeButtonVisible(): Unit`
- `userClicksResume(): Unit`
- `userClicksCancel(): Unit`
- `scanStatusCancelled(): Unit`
- `scanProgressBarVisible(): Unit`
- `scanProgressLabelShowsPercentage(): Unit`
- `scanLogContainerVisible(): Unit`
- `startScanButtonDisabled(): Unit`
- `graphScanStatusBadgeVisible(): Unit`
- `graphNodeCountPresent(): Unit`
- `graphSvgContainerPresent(): Unit`
- `pageDisplaysScanStatusInfo(): Unit`
- `ticketComboboxVisible(): Unit`
- `ticketSearchInputVisible(): Unit`
- `userTypesInTicketSearch(text: String): Unit`
- `ticketDropdownVisible(): Unit`
- `userClicksTicketOption(): Unit`
- `ticketSearchContainsSelectedTicket(): Unit`
- `ticketStatusBadgesPresent(): Unit`
- `actionButtonVisible(): Unit`
- `actionButtonDisabled(): Unit`
- `setUp(): Unit`
- `backendServerIsRunning(): Unit`
- `backendRunningOn(url: String): Unit`
- `backendServerIsNotRunning(): Unit`
- `backendNotReachable(): Unit`
- `userIsAuthenticated(): Unit`
- `userAuthenticatedWithRole(role: String): Unit`
- `userSelectedProject(project: String): Unit`
- `jiraConfiguredWithValidCredentials(): Unit`
- `jiraNotConfigured(): Unit`
- `getRequestMadeTo(path: String): Unit`
- `postRequestMadeTo(path: String): Unit`
- `userOpensRootUrl(): Unit`
- `userOpensApplication(): Unit`
- `userNavigatesTo(hash: String): Unit`
- `userClicksText(text: String): Unit`
- `userClicksDropdownItem(itemText: String): Unit`
- `userClicksAvatar(): Unit`
- `userClicksSidebarItem(itemText: String): Unit`
- `responseStatusShouldBe(status: Int): Unit`
- `backendRespondsWithStatus(status: Int): Unit`
- `browserHashChangesTo(expectedHash: String): Unit`
- `pageDoesNotRedirect(): Unit`
- `pageDisplaysMessage(message: String): Unit`
- `progressBarAnimates(): Unit`
- `errorMessageDisplayed(): Unit`
- `sidebarHighlightsActive(item: String): Unit`
- `knowledgeGraphPageDisplayed(): Unit`
- `jwtRemovedFromStorage(): Unit`
- `roleRemovedFromStorage(): Unit`
- `backendShouldCall(endpoint: String): Unit`
- `userIsOnDashboardPage(): Unit`
- `dashboardDisplaysCardWithPercentage(cardName: String): Unit`
- `dashboardDisplaysCardWithCount(cardName: String): Unit`
- `dashboardDisplaysCardWithScore(cardName: String): Unit`
- `dashboardDisplaysNetworkPreview(): Unit`
- `previewCardContainsSvg(): Unit`
- `previewCardHasButton(buttonText: String): Unit`
- `dashboardDisplaysDriftChart(): Unit`
- `chartCardContainsSvg(): Unit`
- `chartHasButton(buttonText: String): Unit`
- `neuralConsoleDisplaysLogEntries(count: Int): Unit`
- `logEntryIncludesTimestamp(): Unit`
- `logEntryIncludesTag(): Unit`
- `logEntryIncludesMessage(): Unit`
- `tagsInclude(tag1: String, tag2: String, tag3: String): Unit`
- `sidebarDisplaysNavItems(count: Int): Unit`
- `sidebarItemsAre(): Unit`
- `itemHighlightedAsActive(item: String): Unit`
- `userClicksNetworkPreviewButton(buttonText: String): Unit`
- `projectAnalysisPageDisplayed(): Unit`
- `userClicksDriftChartButton(buttonText: String): Unit`
- `dropdownAppearsWithUserInfo(): Unit`
- `dropdownContainsLink(linkText: String): Unit`
- `dropdownDoesNotContainLink(linkText: String): Unit`
- `backendReturns401(endpoint: String): Unit`
- `dashboardStillRendersLayout(): Unit`
- `metricCardsDisplayPlaceholders(): Unit`
- `userSelectedAnalyzedTicket(): Unit`
- `userSelectedNotAnalyzedTicket(): Unit`
- `userSelectedTicketNoBrd(): Unit`
- `userSelectedTicketWithActiveBrdJob(): Unit`
- `userSelectedTicketWithCompletedBrdJob(): Unit`
- `userSelectedTicketWithDraftBrd(): Unit`
- `userSelectedTicketWithApprovedBrd(): Unit`
- `userSelectedTicketWithRejectedBrd(): Unit`
- `thereAreActiveJobs(): Unit`
- `docgenSectionVisible(): Unit`
- `docgenSectionHidden(): Unit`
- `buttonDisplayed(buttonText: String): Unit`
- `buttonDisabled(buttonText: String): Unit`
- `buttonEnabled(buttonText: String): Unit`
- `buttonShowsText(buttonText: String, expectedText: String): Unit`
- `buttonHasTooltip(buttonText: String, tooltip: String): Unit`
- `jobBadgeNotVisible(): Unit`
- `jobBadgeShowsCount(): Unit`
- `badgeDisplayedNextToBrd(badgeText: String): Unit`
- `badgeYellowStyling(): Unit`
- `badgeGreenStyling(): Unit`
- `badgeRedStyling(): Unit`
- `docgenButtonsDisabled(): Unit`
- `frontendCallsActiveJobs(): Unit`
- `frontendCallsDocuments(): Unit`
- `inlineProgressBarVisible(): Unit`
- `progressBarShowsPhaseAndPercent(): Unit`
- `postEstimationWithTicket(path: String, ticketId: String): Unit`
- `estimatorReceivesNaN(): Unit`
- `estimatorReceivesInfinity(): Unit`
- `estimatorReceivesRawScore(score: Double): Unit`
- `unauthenticatedPost(path: String): Unit`
- `responseContainsScrumPoint(): Unit`
- `scrumPointIsValid(): Unit`
- `responseContainsRationale(): Unit`
- `rationaleExplainsReasoning(): Unit`
- `returnsClosestValidPoint(expected: Int): Unit`
- `kbContainsSimilarAnalysis(): Unit`
- `postEstimationWithNewTicket(path: String): Unit`
- `aiReferencesSimilarTickets(): Unit`
- `pointsConsistentWithSimilar(): Unit`
- `jiraNotConfiguredInDb(): Unit`
- `jiraConfiguredWithDomain(domain: String): Unit`
- `userHasValidJwt(): Unit`
- `unauthenticatedRequestMadeTo(endpoint: String): Unit`
- `userNavigatesDirectlyTo(hash: String): Unit`
- `frontendAppCalls(apiCall: String): Unit`
- `responseContains(text: String): Unit`
- `integrationsPageDisplayed(): Unit`
- `toastNotificationAppears(text: String): Unit`
- `dashboardDisplaysEmptyPlaceholders(): Unit`
- `dashboardPageDisplayed(): Unit`
- `tokenContainsClaims(): Unit`
- `tokenHas24HourExpiry(): Unit`
- `backendUsesJiraCredentials(): Unit`
- `requestUsesBasicAuth(): Unit`
- `responseContainsField(field: String): Unit`
- `userCallsLoginEndpoint(endpoint: String): Unit`
- `responseContainsJwt(): Unit`
- `responseContainsUserInfo(): Unit`
- `jwtStoredInKey(key: String): Unit`
- `roleStoredInKey(key: String): Unit`
- `userAuthenticatedWithJwt(): Unit`
- `frontendMakesGet(endpoint: String): Unit`
- `requestContainsHeader(header: String, prefix: String): Unit`
- `noJwtInStorage(): Unit`
- `frontendMakesGetWithoutJwt(endpoint: String): Unit`
- `frontendShouldCall(apiCall: String): Unit`
- `requestIncludesAuth(): Unit`
- `dashboardDisplaysMetricCard(cardName: String): Unit`
- `userAuthenticatedOnDashboard(): Unit`
- `analysisDisplaysFourCards(): Unit`
- `analysisDisplaysSection(section: String): Unit`
- `knowledgeGraphDisplaysSvg(): Unit`
- `pageTitleUpdatesTo(title: String): Unit`
- `userConfiguresJira(domain: String, email: String, token: String): Unit`
- `backendStoresEncryptedCredentials(): Unit`
- `frontendCallsWithJiraData(endpoint: String): Unit`
- `userOnKnowledgeGraphPage(): Unit`
- `jwtClearedFromStorage(): Unit`
- `browserHashRemains(hash: String): Unit`
- `pageDisplaysEmptyState(): Unit`
- `frontendCallsAdminEndpoint(): Unit`
- `frontendDisplaysPermissionDenied(): Unit`
- `frontendAttemptsApiCall(): Unit`
- `frontendDisplaysSpecificMessage(message: String): Unit`
- `userAuthenticatesVia(endpoint: String): Unit`
- `jwtStoredInSession(): Unit`
- `userConfiguresJiraConnection(): Unit`
- `dashboardLoadsData(): Unit`
- `analysisDisplaysMetrics(): Unit`
- `allCallsIncludedAuth(): Unit`
- `responseContainsJiraStatus(): Unit`
- `responseContainsAiStatus(): Unit`
- `responseContainsKbStatus(): Unit`
- `pageTitleContains(expected: String): Unit`
- `livingVoidRendered(): Unit`
- `appContainerPresent(): Unit`
- `consoleLogContains(message: String): Unit`
- `sidebarVisible(): Unit`
- `navbarVisible(): Unit`
- `contentAreaPresent(): Unit`
- `frontendRendersLayout(): Unit`
- `pagesDisplayEmptyStates(): Unit`
- `applicationDoesNotCrash(): Unit`
- `responseContainsSpaHtml(): Unit`
- `cssFilesLoadable(): Unit`
- `jsBundleLoadable(): Unit`
- `userNavigatesToIntegrations(): Unit`
- `pageDisplaysProviderCards(count: Int): Unit`
- `cardsAreExpected(): Unit`
- `gridIsResponsive(): Unit`
- `cardDisplaysLogo(): Unit`
- `cardDisplaysStatusDot(): Unit`
- `cardDisplaysNameAndType(): Unit`
- `cardDisplaysPriority(): Unit`
- `cardHasButtons(button1: String, button2: String): Unit`
- `activeProviderGreenDot(): Unit`
- `standbyProviderBlueDot(): Unit`
- `offlineProviderRedDot(): Unit`
- `userHoversOverStatusDot(): Unit`
- `tooltipAppears(): Unit`
- `userClicksConfigureOnJira(buttonText: String): Unit`
- `modalAppearsWithTitle(title: String): Unit`
- `modalContainsTextInput(label: String): Unit`
- `modalContainsEmailInput(label: String): Unit`
- `modalContainsPasswordInput(label: String): Unit`
- `modalContainsButton(buttonText: String): Unit`
- `modalContainsCloseButton(): Unit`
- `jiraConfigModalOpen(): Unit`
- `userClicksEyeToggle(): Unit`
- `apiTokenFieldChangesToText(): Unit`
- `userClicksEyeToggleAgain(): Unit`
- `apiTokenFieldChangesBackToPassword(): Unit`
- `fieldIsEmpty(fieldLabel: String): Unit`
- `errorMessageWithTextDisplayed(message: String): Unit`
- `noApiCallMade(): Unit`
- `userEntersDomain(domain: String): Unit`
- `userEntersEmail(email: String): Unit`
- `userEntersApiToken(token: String): Unit`
- `buttonTextChangesTo(text: String): Unit`
- `backendValidatesCredentials(): Unit`
- `jiraCardStatusGreen(): Unit`
- `successToastAppears(message: String): Unit`
- `modalClosesAfterDelay(): Unit`
- `backendReturnsStatusWithError(status: String): Unit`
- `jiraCardStatusRed(): Unit`
- `errorInModalStatusArea(): Unit`
- `errorToastAppears(): Unit`
- `jiraConfiguredWithDomainValue(domain: String): Unit`
- `userNavigatesDashboardAndBack(): Unit`
- `jiraCardShowsActive(): Unit`
- `jiraModalPreFillsDomain(): Unit`
- `userClicksOutsideModal(): Unit`
- `modalCloses(): Unit`
- `userClicksCloseButton(): Unit`
- `userClicksConfigureOnOllama(buttonText: String): Unit`
- `userClicksConfigureOnGemini(buttonText: String): Unit`
- `userClicksConfigureOnLmStudio(buttonText: String): Unit`
- `userClicksConfigureOnGeminiCli(buttonText: String): Unit`
- `configModalWithEndpointFields(): Unit`
- `modalHasButton(buttonText: String): Unit`
- `configModalWithApiKeyFields(): Unit`
- `modelTierDropdownIncludes(tier1: String, tier2: String, tier3: String): Unit`
- `configModalWithCliFields(): Unit`
- `userClicksTestLinkOnOllama(buttonText: String): Unit`
- `userClicksTestLinkOnAny(buttonText: String): Unit`
- `buttonDisabledDuringTesting(): Unit`
- `progressBarAnimatesFromZeroTo100(): Unit`
- `resultUpdatesStatusDot(): Unit`
- `buttonResetsAfterCompletion(text: String): Unit`
- `testCompletesWithinTime(seconds: Int): Unit`
- `providersInPriorityOrder(): Unit`
- `userClicksDownArrow(): Unit`
- `providersSwapPositions(): Unit`
- `priorityNumbersUpdate(): Unit`
- `backendPersistsOrder(): Unit`
- `providerCardsVisibleWithInfo(): Unit`
- `buttonsDisabledWithStyles(buttonText: String): Unit`
- `priorityArrowsDisabled(): Unit`
- `buttonsRemainEnabled(buttonText: String): Unit`
- `providerCardsVisible(): Unit`
- `buttonsDisabled(buttonText: String): Unit`
- `userHasNoValidJwt(): Unit`
- `pageDisplaysDefaultCards(count: Int): Unit`
- `cardsShowStandbyOffline(): Unit`
- `pageDisplaysDefaultCardsFallback(count: Int): Unit`
- `userNavigatesToKnowledgeGraph(): Unit`
- `graphSvgVisible(): Unit`
- `graphDisplaysCircleNodes(): Unit`
- `nodesLabeledWithTicketKey(): Unit`
- `featureNodesCyan(): Unit`
- `dependencyNodesBlue(): Unit`
- `uiModuleNodesViolet(): Unit`
- `solidEdgesExplicit(): Unit`
- `dashedEdgesSemantic(): Unit`
- `userHoversOverNode(): Unit`
- `nodeScalesUp(): Unit`
- `nodeDisplaysWhiteBorder(): Unit`
- `userMovesMouseAway(): Unit`
- `nodeReturnsToNormalScale(): Unit`
- `userClicksNode(ticketKey: String): Unit`
- `detailPanelAppears(): Unit`
- `panelDisplaysTicketKey(key: String): Unit`
- `panelDisplaysSummary(): Unit`
- `panelDisplaysDescription(): Unit`
- `panelHasOpenInJiraButton(buttonText: String): Unit`
- `detailPanelOpenForNode(ticketKey: String): Unit`
- `userClicksCloseOnDetailPanel(): Unit`
- `detailPanelHidden(): Unit`
- `graphDisplaysMultipleNodes(): Unit`
- `userTypesInSearch(text: String): Unit`
- `onlyMatchingNodesVisible(text: String): Unit`
- `nonMatchingNodesReducedOpacity(): Unit`
- `onlyMatchingSummaryNodesVisible(text: String): Unit`
- `searchInputContains(text: String): Unit`
- `userClearsSearch(): Unit`
- `allNodesFullyVisible(): Unit`
- `userScrollsUp(): Unit`
- `graphZoomsIn(): Unit`
- `userScrollsDown(): Unit`
- `graphZoomsOut(): Unit`
- `userClicksAndDrags(): Unit`
- `graphPans(): Unit`
- `cursorChanges(cursor: String): Unit`
- `projectHasTickets(count: Int): Unit`
- `graphRendersWithinTime(seconds: Int): Unit`
- `graphContainsClusters(count: Int): Unit`
- `clustersHaveDistinctColors(): Unit`
- `clustersHaveBoundaryRect(): Unit`
- `clusterLabelsUppercase(): Unit`
- `projectHasNoTickets(): Unit`
- `graphDisplaysEmptyState(): Unit`
- `messageIndicatesNoData(): Unit`
- `apiReturns401(endpoint: String): Unit`
- `mcpServersSectionVisible(): Unit`
- `mcpServerCardsDisplayed(): Unit`
- `mcpCardShowsNameAndStatus(): Unit`
- `mcpServerCardVisible(serverName: String): Unit`
- `cardDisplaysBadge(serverName: String, badgeText: String): Unit`
- `cardShowsActiveStatus(serverName: String): Unit`
- `cardNoConfigureButton(serverName: String): Unit`
- `cardNoRemoveButton(serverName: String): Unit`
- `cardNoTestButton(serverName: String): Unit`
- `cardHasStartStopButton(serverName: String): Unit`
- `cardDisplaysToolCount(serverName: String): Unit`
- `activeMcpServersGreenDot(): Unit`
- `offlineMcpServersGreyDot(): Unit`
- `userExpandsToolsSection(serverName: String): Unit`
- `toolsListVisible(): Unit`
- `toolsListContains(toolName: String): Unit`
- `userClicksViewSchema(): Unit`
- `toolSchemaModalVisible(): Unit`
- `schemaModalDisplaysJson(): Unit`
- `toolPermissionCheckboxesVisible(): Unit`
- `userClicksInToolsSection(buttonText: String): Unit`
- `enabledCounterShowsAll(): Unit`
- `enabledCounterShowsZero(): Unit`
- `userClicksAddMcpServer(): Unit`
- `mcpConfigModalVisible(): Unit`
- `modalContainsServerNameField(): Unit`
- `modalContainsCommandField(): Unit`
- `formModeActiveByDefault(): Unit`
- `formHasArgsField(): Unit`
- `formHasEnvField(): Unit`
- `userTogglesToJsonMode(): Unit`
- `jsonEditorVisible(): Unit`
- `userTogglesToFormMode(): Unit`
- `formFieldsVisible(): Unit`
- `mcpExportButtonVisible(): Unit`
- `mcpImportButtonVisible(): Unit`
- `addMcpServerButtonHiddenOrDisabled(): Unit`
- `mcpConfigureButtonsHiddenOrDisabled(): Unit`
- `mcpServerChangesState(): Unit`
- `toastNotificationAppears(): Unit`
- `chatShowsToolExecutionIndicator(): Unit`
- `internalToolCallsDisplayHomeIcon(): Unit`
- `toolResultsInCollapsibleBlocks(): Unit`
- `userAuthenticatesVia(endpoint: String): Unit`
- `jwtContainsClaims(): Unit`
- `tokenSignedWithHmac256(): Unit`
- `frontendMakesAnyRequest(): Unit`
- `requestIncludesHeader(header: String): Unit`
- `userHasValidJwt(): Unit`
- `userHasExpiredJwt(): Unit`
- `userHasMalformedJwt(): Unit`
- `jwtSecretFromEnvVar(envVar: String): Unit`
- `jwtSecretNotInSourceCode(): Unit`
- `frontendAttemptsApiCall(): Unit`
- `frontendDisplaysConnectionError(): Unit`
- `applicationDoesNotCrash(): Unit`
- `providerConfiguredWithApiKey(apiKey: String): Unit`
- `providerConfigSaved(): Unit`
- `apiKeyEncryptedInDb(): Unit`
- `readingConfigReturnsDecrypted(expectedKey: String): Unit`
- `jiraConfiguredWithToken(token: String): Unit`
- `jiraConfigSavedVia(endpoint: String): Unit`
- `credentialsEncryptedWithAes(): Unit`
- `jwtInSessionStorageNotLocal(): Unit`
- `tokenClearedOnTabClose(): Unit`
- `userSuccessfullyAuthenticates(): Unit`
- `auditLogEntryCreated(action: String): Unit`
- `adminChangesUserRole(): Unit`
- `entryContainsOldAndNewRole(): Unit`
- `adminSavesProviderConfig(): Unit`
- `jwtRemovedFromSessionStorage(): Unit`
- `roleRemovedFromSessionStorage(): Unit`
- `serverInvalidatesSession(): Unit`
- `subsequentCallsReturn(status: Int): Unit`
- `frontendSendsInvalidBody(): Unit`
- `responseContainsDescriptiveError(): Unit`
- `userAttemptsAdminEndpoint(): Unit`
- `backendEncountersError(): Unit`
- `responseContainsGenericError(): Unit`
- `errorDoesNotExposeStackTraces(): Unit`
- `dockerComposeAirGapped(): Unit`
- `onlyOllamaAvailable(): Unit`
- `noExternalApiCalls(): Unit`
- `dataRemainsLocal(): Unit`
- `reset(): Unit`
- `isServerReachable(): Boolean`
- `ensureJwt(): Unit`
- `wait(driver: WebDriver): WebDriverWait`
- `wait(driver: WebDriver, timeoutSeconds: Long): WebDriverWait`
- `js(driver: WebDriver): JavascriptExecutor`
- `injectAuth(driver: WebDriver, role: String = "ADMINISTRATOR", project: String = "PROJ"): Unit`
- `injectLightTheme(driver: WebDriver): Unit`
- `clearAuth(driver: WebDriver): Unit`
- `getSessionItem(driver: WebDriver, key: String): String`
- `getHash(driver: WebDriver): String`
- `navigateTo(driver: WebDriver, hash: String): Unit`
- `pageRendered(driver: WebDriver): Boolean`
- `waitForPageSource(driver: WebDriver, text: String): Unit`
- `waitForElement(driver: WebDriver, by: By): Unit`
- `waitForVisible(driver: WebDriver, by: By): Unit`
- `waitForClickable(driver: WebDriver, by: By): Unit`
- `tryClick(driver: WebDriver, by: By): Unit`
- `isServerReachable(): Boolean`
- `waitForOverlayGone(driver: WebDriver, timeoutSeconds: Long = WAIT_TIMEOUT_SECONDS): Unit`
- `userNavigatesToTicketIntelligence(): Unit`
- `pageDisplaysTicketInput(): Unit`
- `pageDisplaysButton(buttonText: String): Unit`
- `progressSectionHidden(): Unit`
- `resultsSectionHidden(): Unit`
- `userEntersTicketId(ticketId: String): Unit`
- `userClicksNamedButton(buttonText: String): Unit`
- `progressSectionVisible(): Unit`
- `progressBarAnimatesFullRange(): Unit`
- `statusTickerShowsPhases(): Unit`
- `analysisCompletes(): Unit`
- `resultsSectionVisibleWith3Tabs(): Unit`
- `analysisResultDisplayedFor(ticketId: String): Unit`
- `analysisResultDisplayed(): Unit`
- `tabIsActive(tabName: String): Unit`
- `userClicksTab(tabName: String): Unit`
- `userClicksTabButton(tabName: String): Unit`
- `tabDisplaysTicketKeyAndSummary(): Unit`
- `tabListsAffectedModules(): Unit`
- `tabDisplaysScrumPoint(): Unit`
- `tabDisplaysRationale(): Unit`
- `tabDisplaysRelatedTickets(): Unit`
- `tabButtonHasActiveClass(tabName: String): Unit`
- `tabButtonDoesNotHaveActiveClass(tabName: String): Unit`
- `estimationTabContentVisible(): Unit`
- `summaryTabContentHidden(): Unit`
- `ticketPreviouslyAnalyzed(ticketId: String): Unit`
- `userEntersTicketAndClicks(ticketId: String, buttonText: String): Unit`
- `systemReturnsCachedResult(): Unit`
- `noAiAgentInvoked(): Unit`
- `ticketHasCachedResult(ticketId: String): Unit`
- `orchestratorPerformsFreshAnalysis(): Unit`
- `analysisCompletesForAnyTicket(): Unit`
- `scrumPointInValidScale(): Unit`
- `buttonShouldBeDisabled(buttonText: String): Unit`
- `buttonHasDisabledStyles(): Unit`
- `buttonShouldBeEnabled(buttonText: String): Unit`
- `analysisIsLongRunning(ticketId: String, seconds: Int): Unit`
- `frontendStartsPolling(endpoint: String, interval: Int): Unit`
- `progressBarUpdatesFromPolling(): Unit`
- `pollingStops(): Unit`
- `resultsDisplayed(): Unit`
- `backendReturnsError(endpoint: String): Unit`
- `progressBarCompletes(): Unit`
- `userCanRetry(): Unit`
- `aiProviderReturnsInvalidJson(): Unit`
- `analysisTriggered(): Unit`
- `orchestratorRetries(times: Int): Unit`
- `retriesFailShowError(): Unit`
- `ticketIdInputEmpty(): Unit`
- `systemDoesNotMakeApiCall(): Unit`
- `validationMessageShown(): Unit`
- `userNavigatesToUserManagement(): Unit`
- `userManagementPageDisplayed(): Unit`
- `sidebarDoesNotContainDirectLink(linkText: String): Unit`
- `userManagementOnlyViaDropdown(): Unit`
- `pageDisplaysUserList(): Unit`
- `userRowShowsExpectedElements(): Unit`
- `userCurrentlyHasRole(email: String, role: String): Unit`
- `adminSelectsRoleForUser(newRole: String, email: String): Unit`
- `rbacEngineAppliesChange(): Unit`
- `userNowHasRole(email: String, role: String): Unit`
- `permissionPanelDisplaysToggles(count: Int): Unit`
- `togglesAreExpected(): Unit`
- `permissionPanelDisplayed(): Unit`
- `adminTogglesPermission(permissionName: String): Unit`
- `syncIndicatorDisplays(text: String): Unit`
- `syncIndicatorDisplaysWhenFinished(text: String): Unit`
- `adminChangesRole(): Unit`
- `auditLogContainsEntry(): Unit`
- `neuralConsoleDisplaysAuditEntries(): Unit`
- `entryIncludesTimestampTagDescription(): Unit`
- `tagsInclude(tag1: String, tag2: String): Unit`
- `userListNotVisible(): Unit`
- `adminRoleHasAllPermissions(role: String): Unit`
- `neuralArchitectRoleHasPermissions(role: String): Unit`
- `roleDoesNotHaveAdminPermissions(role: String): Unit`
- `readerRoleHasViewPermissions(role: String): Unit`
- `readerDoesNotHaveWritePermissions(role: String): Unit`
- `analyzeButtonDisabledClientSide(buttonText: String): Unit`
- `directApiCallReturnsStatus(endpoint: String, expectedStatus: Int): Unit`
- `userHasRole(email: String, role: String): Unit`
- `adminChangesRoleTo(newRole: String): Unit`
- `nextRequestUsesNewPermissions(email: String): Unit`

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| — | — |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: unknown
- **Naming**: unknown
- **Logging**: unknown
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
