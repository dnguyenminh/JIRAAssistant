# BRD — ICL2-15

- **Type:** BRD
- **Ticket:** ICL2-15
- **Generated:** 2026-04-19T15:06:47.546555400Z
- **Source Tickets:** ICL2-15, CRP-84, ICL2-26, ICL2-703, ICL2-705, ICL2-707, ICL2-706, ICL2-708, ICL2-711, ICL2-717, ICL2-719, ICL2-720, ICL2-721, ICL2-722, ICL2-682, ICL2-686, ICL2-693, ICL2-694, ICL2-870, ICL2-1013, ICL2-1014, ICL2-1015, ICL2-1016, ICL2-1017, ICL2-1261, ICL2-1262, CRP-482, BO-150033, BO-150259, ITCDR-2141, ICL2-1343, ITCDR-2545, ICL2-1364, ICL2-1367, ICL2-1375, ICL2-1376
- **AI Provider:** Local Ollama - batiai/gemma4-e2b:q4

---
## Revision History

| Version | Date | Author | Changes |
| :--- | :--- | :--- | :--- |
| v1.0 | 14 May 2024 | Quang Lam | Initiate document |
| v1.1 | 03 June 2024 | Quang Lam | Update information as commented: Remove sFTP, Update CA, Describing contract information usage. |
| v1.2 | 07 June 2024 | Quang Lam | Update sFTP link, Update columns in `COLL_OPS.COLLX_LETTER` (COLLECTOR, COLLECTOR\_NO). |
| v1.3 | 20 June 2024 | Quang Lam | Update flow, add sending email process, remove "RUN\_DATE" folder creation. |
| v1.4 | 22 July 2024 | Quang Lam | New templates for DKK, TBKK, CBTNHS, Mapping database for all contract types. |
| v1.5 | 01 Aug 2024 | Quang Lam | User download via iCollect portal instead of sFTP, introduced Bulk upload screen, split into two User Stories. |
| v1.6 | 22 Aug 2024 | Quang Lam | Data storage changed to 4 days, Password sent via email, Bulk upload via portal, User Story split. |
| v2.0 | 13 Aug 2024 | Quang Lam | Updated requirements based on BU feedback (e.g., `CARD_LIMIT` handling). |
| v2.1 | 29 Oct 2024 | Quang Lam | Updated requirements based on further feedback, including data masking and logging. |
| v2.2 | 19 Nov 2024 | Quang Lam | Added information regarding Court/Court details (Tòa án) and finalization of PDF/QR code logic. |
| v2.3 | 19 Nov 2024 | Quang Lam | Final version incorporating all feedback, including QR code format details, and ready for final review. |

## Project Overview

⚠️ Insufficient data — This section requires manual input. Available data from analysis: none

## Common Project Acronyms, Names, and Descriptions

| Acronym/Term | Full Name/Description | Context/Source |
| :--- | :--- | :--- |
| **iCollect** | iCollect Portal | Core system where Collection Letters are generated and uploaded. |
| **CR** | Change Request | Used for tracking feature requests (e.g., CRP-84). |
| **BRD** | Business Requirements Document | The formal document detailing the requirements for the feature. |
| **PDF** | Portable Document Format | The file format for the generated Collection Letters. |
| **Bulk Upload** | Bulk Upload | The mechanism for uploading multiple contract files simultaneously via the portal. |
| **DKK, TBKK, CBTNHS** | Đơn Khởi Kiện, Thông báo Khởi kiện, Cảnh báo Trách nhiệm Hình sự | Specific PDF templates involved in the merging process. |
| **iCollect Portal** | iCollect Portal | The user interface for uploading files and downloading results. |
| **COLL\_OPS.COLLX\_LETTER** | Database table storing contract/letter data. | Database structure used for storing bulk upload data. |
| **Nifi** | Apache NiFi | Technology used for performance optimization of bulk processing. |
| **ITSMO/ITSO** | Information Technology Support/Service Operation | Internal support teams involved in system integration and deployment. |

## Existing Processes

⚠️ Insufficient data — This section requires manual input. Available data from analysis: none

## Project Requirements

⚠️ Insufficient data — This section requires manual input. Available data from analysis: none

## Sign Off

| Role | Name | Signature and Date | Status |
| :--- | :--- | :--- | :--- |
| Business Owner | [Stakeholder Name] | Pending | Awaiting final data confirmation for Card Limit. |
| Ops Risk | Duc Nguyen Minh | Pending | Awaiting final review of impact assessment. |
| IT/ITS | ITSO/ITPMO | Pending | Awaiting final deployment plan confirmation. |
| Business Unit | BU | Pending | Awaiting final UAT sign-off on functional requirements. |

## Appendix

⚠️ Insufficient data — This section requires manual input. Available data from analysis: none