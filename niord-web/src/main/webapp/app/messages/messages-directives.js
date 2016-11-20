/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Message list directives.
 */
angular.module('niord.messages')


    /********************************
     * Renders a badge with the message
     * ID and a colour that signals the
     * current status
     ********************************/
    .directive('messageIdBadge', ['LangService', function (LangService) {
        'use strict';

        return {
            restrict: 'E',
            template: '<span class="label label-message-id" ng-class="messageClass">{{shortId}}</span>{{suffix}}',
            scope: {
                msg:       "=",
                showBlank: "="
            },
            link: function(scope) {

                function updateSuffix() {
                    var msg = scope.msg;
                    if (msg && msg.mainType == 'NM' &&
                        (msg.type == 'TEMPORARY_NOTICE' || msg.type == 'PRELIMINARY_NOTICE')) {
                        scope.suffix = msg.type == 'TEMPORARY_NOTICE' ? ' (T)' : ' (P)';
                    }
                }

                /** Updates the label based on the current status and short ID **/
                function updateIdLabel() {
                    scope.suffix = '';
                    var msg = scope.msg;
                    var status = msg && msg.status ? msg.status : 'DRAFT';
                    scope.messageClass = 'status-' + status;

                    scope.shortId = '';
                    if (msg && msg.shortId) {
                        scope.shortId = msg.shortId;
                        updateSuffix();
                    } else if (scope.showBlank) {
                        scope.shortId = msg.type ? LangService.translate('msg.type.' + msg.type) + ' ' : '';
                        scope.shortId = scope.shortId + (msg.mainType ? msg.mainType : '');
                        scope.messageClass += '-outline';
                    }
                }

                scope.$watch('[msg.shortId, msg.status, msg.mainType, msg.type]', updateIdLabel, true);
            }
        }
    }])

    /****************************************************************
     * Replaces the content of the element with the area description
     ****************************************************************/
    .directive('renderAreas', ['LangService', function (LangService) {
        return {
            restrict: 'A',
            scope: {
                renderAreas: "=",
                areaDivider: "@"
            },
            link: function(scope, element, attrs) {
                var divider = (attrs.areaDivider) ? attrs.areaDivider : " - ";

                /** Prepends the prefix to the result **/
                function prepend(prefix, result) {
                    return prefix
                        + ((result.length > 0 && prefix.length > 0) ? divider : '')
                        + result;
                }

                scope.updateAreas = function(areas) {
                    var result = '';
                    if (areas && areas.length > 0) {
                        for (var area = areas[0]; area; area = area.parent) {
                            if (area.id == -999999) {
                                // Special "General" area used for messages without an assigned area
                                result = prepend(LangService.translate('msg.area.general'), result);
                            } else {
                                var desc = LangService.desc(area);
                                var areaName = (desc) ? desc.name : '';
                                result = prepend(areaName, result);
                            }
                        }
                    }
                    element.html(result);
                };

                scope.$watchCollection("renderAreas", scope.updateAreas);
            }
        };
    }])


    /****************************************************************
     * Replaces the content of the element with the chart list
     ****************************************************************/
    .directive('renderCharts', [function () {
        return {
            restrict: 'A',
            scope: {
                renderCharts: "="
            },
            link: function(scope, element) {
                scope.updateCharts = function(charts) {
                    var result = '';
                    if (charts && charts.length > 0) {
                        for (var x = 0; x < charts.length; x++) {
                            var chart = charts[x];
                            if (x > 0) {
                                result += ', ';
                            }
                            result += chart.chartNumber;
                            if (chart.internationalNumber) {
                                result += ' (INT ' + chart.internationalNumber + ')';
                            }
                        }
                    }
                    element.html(result);
                };

                scope.$watchCollection("renderCharts", scope.updateCharts);
            }
        };
    }])


    /****************************************************************
     * Prints the event dates of all message parts
     ****************************************************************/
    .directive('renderMessageDates', ['$rootScope', 'DateIntervalService', function ($rootScope, DateIntervalService) {
        return {
            restrict: 'E',
            scope: {
                msg: "=",
                firstLine: "="
            },
            link: function(scope, element) {

                scope.updateTime = function () {
                    var lang = $rootScope.language;
                    var time = '';
                    // First check for a textual time description
                    if (scope.msg.parts && scope.msg.parts.length > 0) {
                        for (var p = 0; p < scope.msg.parts.length; p++) {
                            var eventDates = scope.msg.parts[p].eventDates;
                            for (var x = 0; eventDates && x < eventDates.length; x++) {
                                time += DateIntervalService.translateDateInterval(lang, eventDates[x], true) + "<br>";
                            }
                        }
                    }
                    time = time.replace(/<br>$/g, '');

                    if (scope.firstLine) {
                        var index = time.indexOf("<br>");
                        if (index != -1) {
                            time = time.substr(0, index) +
                                ' <span style="color:red; cursor: pointer" title="' +
                                time.replace(/<br>/g, '\n') + '">&hellip;</span>';
                        }
                    }
                    element.html(time);
                };

                scope.$watch("msg", scope.updateTime);
            }
        };
    }])


    /****************************************************************
     * Prints the message part event dates
     ****************************************************************/
    .directive('renderEventDates', ['$rootScope', 'DateIntervalService', function ($rootScope, DateIntervalService) {
        return {
            restrict: 'E',
            scope: {
                part: "="
            },
            link: function(scope, element) {

                scope.updateTime = function () {
                    var lang = $rootScope.language;
                    var time = '';
                    if (scope.part.eventDates && scope.part.eventDates.length > 0) {
                        for (var x = 0; x < scope.part.eventDates.length; x++) {
                            time += DateIntervalService.translateDateInterval(lang, scope.part.eventDates[x], true) + "<br>";
                        }
                    }
                    time = time.replace(/<br>$/g, '');
                    element.html(time);
                };

                scope.$watch("part", scope.updateTime);
            }
        };
    }])


    /****************************************************************
     * Renders the message publication and, optionally, allows the
     * user to see the internal publication links as well
     ****************************************************************/
    .directive('renderMessagePublication', ['$rootScope', 'LangService',
        function ($rootScope, LangService) {

        return {
            restrict: 'E',
            template: '<span class="message-publication" ng-bind-html="publication | toTrusted"></span>',
            scope: {
                msg: "=",
                lang: "="
            },
            link: function(scope) {

                scope.lang = scope.lang || $rootScope.language;
                scope.publication = '';

                scope.updatePublication = function () {
                    scope.publication = '';

                    if (scope.msg) {
                        var desc = LangService.desc(scope.msg, scope.lang);
                        if (desc && desc.publication) {
                            scope.publication = desc.publication;
                        }
                        if (desc && desc.internalPublication && $rootScope.isLoggedIn) {
                            scope.publication += ' ' + desc.internalPublication;
                        }
                    }
                };

                scope.$watch("msg", scope.updatePublication);
            }
        };
    }])


    /****************************************************************
     * The message-attachment directive renders an attachment
     ****************************************************************/
    .directive('messageAttachment', [function () {
        return {
            restrict: 'E',
            templateUrl: '/app/messages/message-attachment.html',
            replace: true,
            scope: {
                attachment: "=",
                size: "@",
                imageType: "@",
                labelType: "@",
                attachmentClicked: '&'
            },
            link: function(scope, element, attrs) {

                scope.imageType = scope.imageType || 'thumbnail';
                scope.labelType = scope.labelType || 'file-name';

                var filePath = scope.attachment.path;
                scope.thumbnailUrl = "/rest/repo/thumb/" + filePath + "?size=" + scope.size;
                scope.fileUrl = "/rest/repo/file/" + filePath;


                scope.imageClass = "attachment-image";
                if (scope.imageType == "thumbnail") {
                    scope.imageClass += " size-" + scope.size;
                }
                if (scope.attachment.type && scope.attachment.type.toLowerCase().indexOf("image") == 0) {
                    scope.imageClass += " attachment-image-shadow";
                }
                
                scope.sourceStyle = {};
                if (scope.imageType == "source") {
                    scope.sourceType = (scope.attachment.type && scope.attachment.type.startsWith('video'))
                        ? "video"
                        : 'image';
                    if (scope.attachment.width && scope.attachment.height) {
                        scope.sourceStyle = { width: scope.attachment.width, height: scope.attachment.height };
                    } else if (scope.attachment.width) {
                        scope.sourceStyle = { width: scope.attachment.width };
                    } else if (scope.attachment.height) {
                        scope.sourceStyle = { height: scope.attachment.height };
                    }
                    scope.sourceStyle['max-width'] = '100%';
                }
                

                scope.handleClick = attrs.attachmentClicked !== undefined;
                scope.tooltip = scope.labelType != 'caption' && scope.attachment.descs && scope.attachment.descs.length > 0
                        ? scope.attachment.descs[0].caption
                        : '';

                scope.click = function() {
                    if (scope.handleClick) {
                        scope.attachmentClicked({ attachment: scope.attachment })
                    }
                }
            }
        };
    }])



    /********************************
     * Defines a message id field, e.g
     * used for references.
     ********************************/
    .directive('messageIdField', [ '$http', '$rootScope', function ($http, $rootScope) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/messages/message-id-field.html',
            replace: false,
            scope: {
                reference:  "=",
                minLength:  "=",
                deleted:    "=",
                tabIndex:   "="
            },
            link: function(scope, element) {

                scope.minLength = scope.minLength | 3;

                if (scope.tabIndex) {
                    var input = element.find("input");
                    input.attr('tabindex', scope.tabIndex);
                }

                // Use for message id selection
                scope.messageIds = [];
                scope.refreshMessageIds = function (text) {
                    if (!text || text.length < scope.minLength) {
                        return [];
                    }
                    return $http.get(
                        '/rest/messages/search-message-ids?txt=' + encodeURIComponent(text) +
                        '&deleted=' + (scope.deleted === true) +
                        '&lang=' + $rootScope.language
                    ).then(function(response) {
                        scope.messageIds = response.data;
                    });
                };

            }
        }
    }])


    /****************************************************************
     * The message-tags-field directive supports selecting either a
     * single tag or a list of tags. For single-tag selection use
     * tagData.tag and for multi-tag selection use tagData.tags.
     * Use "init-ids" to initialize the tags using a list of tag ids.
     ****************************************************************/
    .directive('messageTagsField', ['$http', 'MessageService', function($http, MessageService) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/messages/message-tags-field.html',
            scope: {
                tagData:    "=",
                initIds:    "=",
                multiple:   "="
            },
            link: function(scope) {

                scope.tagData = scope.tagData || {};
                scope.multiple = scope.multiple || false;
                if (scope.multiple && !scope.tagData.tags) {
                    scope.tagData.tags = [];
                }


                // init-ids can be used to instantiate the field from a list of tags IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/tags/tag/' + initIds.join()).then(function(response) {
                            // Reset the initId array
                            initIds.length = 0;
                            // Update the loaded entities
                            angular.forEach(response.data, function (tag) {
                                if (scope.multiple) {
                                    scope.tagData.tags.push(tag);
                                } else {
                                    scope.tagData.tag = tag;
                                }
                            });
                        });
                    }
                }, true);


                /** Refreshes the tags search result */
                scope.searchResult = [];
                scope.refreshTags = function(name) {
                    if (!name || name.length == 0) {
                        return [];
                    }
                    return $http.get(
                        '/rest/tags/search?name=' + encodeURIComponent(name) + '&limit=10'
                    ).then(function(response) {
                        scope.searchResult = response.data;
                    });
                };


                /** Opens the tags dialog */
                scope.openTagsDialog = function () {
                    MessageService.messageTagsDialog().result
                        .then(function (tag) {
                            if (tag && scope.multiple) {
                                scope.tagData.tags.push(tag)
                            } else if (tag) {
                                scope.tagData.tag = tag;
                            }
                        });
                };


                /** Removes the current tag selection */
                scope.removeTag = function () {
                    if (scope.multiple) {
                        scope.tagData.tags.length = 0;
                    } else {
                        scope.tagData.tag = undefined;
                    }
                };
            }
        }
    }])


    /****************************************************************
     * The message-series-field directive supports selecting either a
     * single message series or a list of message series.
     * Use "init-ids" to initialize the message series using a list of
     * message series ids.
     ****************************************************************/
    .directive('messageSeriesField', ['$rootScope', '$http', function($rootScope, $http) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/messages/message-series-field.html',
            scope: {
                seriesData:     "=",
                initIds:        "=",
                domain:         "=",
                multiple:       "="
            },
            link: function(scope) {

                scope.seriesData = scope.seriesData || {};

                // Message series search parameters
                scope.multiple = scope.multiple || false;

                if (scope.multiple && !scope.seriesData.messageSeries) {
                    scope.seriesData.messageSeries = [];
                }

                // init-ids can be used to instantiate the field from a list of message series IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/message-series/search/' + initIds.join() + '?lang=' + $rootScope.language + '&limit=20')
                            .then(function(response) {
                                // Reset the initId array
                                initIds.length = 0;
                                // Update the loaded entities
                                angular.forEach(response.data, function (series) {
                                    if (scope.multiple) {
                                        scope.seriesData.messageSeries.push(series);
                                    } else {
                                        scope.seriesData.messageSeries = series;
                                    }
                                });
                            });
                    }
                }, true);


                /** Refreshes the message series search result */
                scope.searchResult = [];
                scope.refreshMessageSeries = function(name) {
                    if (!name || name.length == 0) {
                        return [];
                    }
                    var domainParam = scope.domain ? '&domain=' + encodeURIComponent(scope.domain.domainId) : '';
                    return $http.get(
                        '/rest/message-series/search?name=' + encodeURIComponent(name) +
                        domainParam +
                        '&lang=' + $rootScope.language +
                        '&limit=10'
                    ).then(function(response) {
                        scope.searchResult = response.data;
                    });
                };


                /** Removes the current message series selection */
                scope.removeMessageSeries = function () {
                    if (scope.multiple) {
                        scope.seriesData.messageSeries.length = 0;
                    } else {
                        scope.seriesData.messageSeries = undefined;
                    }
                };
            }
        }
    }])


    /****************************************************************
     * Binds a click event that will open the message details dialog
     ****************************************************************/
    .directive('messageDetailsLink', ['MessageService',
        function (MessageService) {
            'use strict';

            return {
                restrict: 'A',
                scope: {
                    messageDetailsLink: "=",
                    messageList: "=",
                    selection: "=",
                    disabled: "=?"
                },
                link: function(scope, element) {

                    if (!scope.disabled) {
                        element.addClass('clickable');
                        element.bind('click', function() {
                            MessageService.detailsDialog(scope.messageDetailsLink, scope.messageList, scope.selection);
                        });
                    }
                }
            };
        }])


    /****************************************************************
     * Binds a click event that will open the message details dialog
     ****************************************************************/
    .directive('loadMoreMessages', [
        function () {
            'use strict';

            return {
                restrict: 'A',
                templateUrl: '/app/messages/load-more-messages.html',
                replace: false,
                scope: {
                    loadMoreMessages:    "=",
                    totalMessageNo: "=",
                    maxSize:        "=",
                    loadMore:       "&"
                },
                link: function(scope) {

                    scope.messageList = scope.loadMoreMessages;

                    scope.fromMessageNo = function () {
                        return numeral(scope.messageList.length).format("0,0");
                    };

                    scope.toMessageNo = function () {
                        return numeral(Math.min(scope.messageList.length + scope.maxSize, scope.totalMessageNo)).format("0,0");
                    };

                    scope.total = function () {
                        return numeral(scope.totalMessageNo).format("0,0");
                    };

                    /** Called to load next batch of messages **/
                    scope.loadMoreMessages = function () {
                        scope.loadMore();
                    }
                }
            };
        }])


    /********************************
     * Renders the message details
     ********************************/
    .directive('renderMessageDetails', [ '$rootScope', 'MapService', 'MessageService',
        function ($rootScope, MapService, MessageService) {
        'use strict';

        return {
            restrict: 'A',
            templateUrl: '/app/messages/render-message-details.html',
            replace: false,
            scope: {
                msg:            "=",
                messageList:    "=",
                language:       "=",
                selection:      "=",
                format:         "@",
                showDetailsMenu:"@",
                showDetails:    "&",
                compact:        "="
            },
            link: function(scope, element, attrs) {
                scope.language = scope.language || $rootScope.language;
                scope.format = scope.format || 'list';
                scope.attachmentsAbove = [];
                scope.attachmentsBelow = [];
                scope.showAttachments = scope.compact || false;
                scope.internalPublications = $rootScope.isLoggedIn && scope.format == 'details';


                // Returns if the given message is selected or not
                scope.isSelected = function () {
                    return scope.selection.get(scope.msg.id) !== undefined;
                };


                // Toggle the selection state of the message
                scope.toggleSelectMessage = function () {
                    if (scope.isSelected()) {
                        scope.selection.remove(scope.msg.id);
                    } else if (scope.msg) {
                        scope.selection.put(scope.msg.id, angular.copy(scope.msg));
                    }
                };


                /** Called when a message reference is clicked **/
                scope.referenceClicked = function(messageId) {
                    if (attrs.showDetails) {
                        scope.showDetails({messageId: messageId});
                    } else {
                        MessageService.detailsDialog(messageId, scope.messageList);
                    }
                };


                /** Called whenever the message changes **/
                scope.initMessage = function () {
                    scope.attachmentsAbove.length = 0;
                    scope.attachmentsBelow.length = 0;

                    // Extract the attachments that will displayed above and below the message data
                    if (scope.msg.attachments) {
                        scope.attachmentsAbove = $.grep(scope.msg.attachments, function (att) {
                            return att.display == 'ABOVE';
                        });
                        scope.attachmentsBelow = $.grep(scope.msg.attachments, function (att) {
                            return att.display == 'BELOW';
                        });
                    }
                };


                // Sets whether to show the attachments or not
                scope.setShowAttachments = function (value) {
                    scope.showAttachments = value;
                };

                scope.$watch("msg", scope.initMessage);
            }
        };
    }])


    /********************************
     * Renders the message part
     ********************************/
    .directive('renderMessagePart', [
        function () {
            'use strict';

            return {
                restrict: 'E',
                templateUrl: '/app/messages/render-message-part.html',
                replace: false,
                scope: {
                    part:        "=",
                    subject:     "="
                },
                link: function(scope, element, attrs) {
                }
            };
        }])


    /****************************************************************
     * Adds a message details drop-down menu
     ****************************************************************/
    .directive('messageDetailsMenu', ['$rootScope', '$window', '$state', 'growl', 'MessageService', 'DialogService',
        function ($rootScope, $window, $state, growl, MessageService, DialogService) {
            'use strict';

            return {
                restrict: 'E',
                templateUrl: '/app/messages/message-details-menu.html',
                scope: {
                    messageId:      "=",     // NB: We supply both of "messageId" and "msg" because
                    msg:            "=",     // the former may be invalid and the latter may be undefined.
                    messages:       "=",
                    style:          "@",
                    size:           "@",
                    dismissAction:  "&"
                },
                link: function(scope, element) {

                    if (scope.style) {
                        element.attr('style', scope.style);
                    }

                    if (scope.size) {
                        $(element[0]).find('button').addClass("btn-" + scope.size);
                    }

                    scope.hasRole = $rootScope.hasRole;
                    scope.isLoggedIn = $rootScope.isLoggedIn;
                    scope.messageTags = [];


                    /** Called when the dropdown is about to be displayed **/
                    scope.menuClicked = function() {
                        if (scope.isLoggedIn) {
                            var tag = MessageService.getLastMessageTagSelection();
                            scope.lastSelectedMessageTag = (tag) ? tag.name : undefined;

                            MessageService.tagsForMessage(scope.messageId)
                                .success(function (messageTags) {
                                    scope.messageTags = messageTags;

                                    // If the current message is already associated with the last selected tag,
                                    // Do not provide a link to add it again.
                                    for (var x = 0; x < messageTags.length; x++) {
                                        if (tag && tag.tagId == messageTags[x].tagId) {
                                            scope.lastSelectedMessageTag = undefined;
                                        }
                                    }
                                })
                        }
                    };


                    /** Adds the current message to the given tag **/
                    scope.addToTag = function (tag) {
                        if (tag) {
                            MessageService.addMessagesToTag(tag, [ scope.messageId ])
                                .success(function () {
                                    growl.info("Added message to " + tag.name, { ttl: 3000 })
                                })
                        }
                    };


                    /** Adds the current message to the tag selected via the Message Tag dialog */
                    scope.addToTagDialog = function () {
                        MessageService.messageTagsDialog().result
                            .then(scope.addToTag);
                    };


                    /** Adds the current message to the last selected tag */
                    scope.addToLastSelectedTag = function () {
                        scope.addToTag(MessageService.getLastMessageTagSelection());
                    };


                    /** Removes the current message from the given tag */
                    scope.removeFromTag = function (tag) {
                        MessageService.removeMessagesFromTag(tag, [ scope.messageId ])
                            .success(function () {
                                growl.info("Removed message from " + tag.name, { ttl: 3000 })
                            })
                    };

                    
                    /** Opens the message print dialog */
                    scope.pdf = function () {
                        MessageService.printMessage(scope.messageId);
                    };


                    /** Returns if the user can edit the current message */
                    scope.canEdit = function () {
                        return scope.hasRole('editor') &&
                                scope.msg && scope.msg.messageSeries &&
                                $rootScope.domain && $rootScope.domain.messageSeries &&
                                $.grep($rootScope.domain.messageSeries, function (ms) {
                                    return ms.seriesId == scope.msg.messageSeries.seriesId;
                                }).length > 0;
                    };

                    
                    /** Navigate to the message editor page **/
                    scope.edit = function() {
                        if (scope.dismissAction) {
                            scope.dismissAction();
                        }
                    };

                    /** Copies the message **/
                    scope.copy = function () {
                        DialogService.showConfirmDialog(
                            "Copy Message?", "Copy Message?")
                            .then(function() {
                                // Navigate to the message editor page
                                $state.go(
                                    'editor.copy',
                                    { id: scope.messageId,  referenceType : 'REFERENCE' },
                                    { reload: true }
                                );
                                if (scope.dismissAction) {
                                    scope.dismissAction();
                                }
                            });
                    };
                }
            }
        }]);

