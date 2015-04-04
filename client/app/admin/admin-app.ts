/*
 * Copyright (C) 2015 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/// <reference path="../../typedefs/react/react.d.ts" />
/// <reference path="../../shared/plain-old-javascript.d.ts" />
/// <reference path="../ReactStore.ts" />
/// <reference path="../Server.ts" />
/// <reference path="settings.ts" />

//------------------------------------------------------------------------------
   module debiki2.admin {
//------------------------------------------------------------------------------

var d = { i: debiki.internal, u: debiki.v0.util };
var r = React.DOM;
var ReactCSSTransitionGroup = React.addons.CSSTransitionGroup;
var reactCreateFactory = React['createFactory'];

var ReactBootstrap: any = window['ReactBootstrap'];
var Nav = reactCreateFactory(ReactBootstrap.Nav);
var NavItem = reactCreateFactory(ReactBootstrap.NavItem);
var TabbedArea = reactCreateFactory(ReactBootstrap.TabbedArea);
var TabPane = reactCreateFactory(ReactBootstrap.TabPane);
var Button = reactCreateFactory(ReactBootstrap.Button);

var ReactRouter = window['ReactRouter'];
var Route = ReactRouter.Route;
var Redirect = ReactRouter.Redirect;
var DefaultRoute = ReactRouter.DefaultRoute;
var NotFoundRoute = ReactRouter.NotFoundRoute;
var RouteHandler = ReactRouter.RouteHandler;
var Navigation = ReactRouter.Navigation;
var State = ReactRouter.State;


export function routes() {
  return Route({ path: '/', handler: AdminApp },
    Redirect({ from: '/', to: 'settings' }),
    Route({ name: 'settings', path: 'settings', handler: SettingsPanel }),
    Route({ name: 'customize', path: 'customize', handler: CustomizePanel }),
    Route({ name: 'moderate', path: 'moderate', handler: ModerationPanel }));
}



var AdminApp = createComponent({
  mixins: [Navigation, State],

  getInitialState: function() {
    return {
      activeRoute: this.getRoutes()[1].name
    };
  },

  handleSelect: function(newRoute) {
    this.setState({ activeRoute: newRoute });
    this.transitionTo(newRoute);
  },

  render: function() {
    return (
      r.div({ className: 'admin-app' },
        Nav({ bsStyle: 'pills', activeKey: this.state.activeRoute, onSelect: this.handleSelect },
          NavItem({ eventKey: 'settings' }, 'Settings'),
          NavItem({ eventKey: 'customize' }, 'Customize'),
          NavItem({ eventKey: 'moderate' }, 'Moderate')),
        RouteHandler({})));
  }
});



var SettingsPanel = createComponent({
  mixins: [SaveSettingMixin],

  componentDidMount: function() {
    Server.loadSettings('WholeSite', null, settings => {
      this.setState(settings);
    });
  },

  render: function() {
    if (!this.state)
      return r.p({}, 'Loading...');

    var settings = this.state;
    var saveSetting = this.saveSetting;
    var termsOfUseLink = r.a({ href: '/-/terms-of-use', target: '_blank' },
        'Terms of Use');

    return (
      r.div({},
        Setting({ setting: settings.title, onSave: saveSetting, label: 'Title',
          help: 'The site title, will be used in the title tag and elsewhere.' }),

        Setting({ setting: settings.description, onSave: saveSetting,
          label: 'Description', help: 'A one sentence description of the website. ' +
              'Will be used in the meta description tag.' }),

        Setting({ setting: settings.googleUniversalAnalyticsTrackingId,
          onSave: saveSetting, label: 'Google Universal Analytics tracking ID',
          help: r.span({}, 'Any Google Universal Analytics tracking ID, e.g. ',
              r.samp({}, 'UA-12345678-9'), ', see http://google.com/analytics.') }),

        Setting({ setting: settings.companyFullName, onSave: saveSetting,
          label: 'company_full_name', help: r.span({}, "The full name of the company " +
              "or organization that runs this site. Used in legal documents " +
              "like the ", termsOfUseLink, " page.") }),

        Setting({ setting: settings.companyShortName, onSave: saveSetting,
          label: 'company_short_name', help: r.span({}, "The short name of the company " +
              "or organization that runs this site. Used in legal documents " +
              "like the ", termsOfUseLink, " page.") }),

        Setting({ setting: settings.companyDomain, onSave: saveSetting,
          label: 'company_domain', help: r.span({}, "The domain name owned by the company " +
              "that runs this site. Used in legal documents like the ", termsOfUseLink, ".") }),

        SpecialContent({ contentId: '_tou_content_license',
            label: 'Terms of Use: Content License',
            help: r.span({}, "Please clarify under which license other people may reuse " +
                "the contents of the website. This text will be inserted into " +
                "the Content License section of your ", termsOfUseLink, " page. " +
                "By default, content is licensed under a Creative Commonts license " +
                "(see below) so you can just leave this as is.") }),

        SpecialContent({ contentId: '_tou_jurisdiction',
            label: 'Terms of Use: Jurisdiction',
            help: r.span({}, "Please clarify which country's laws you want to abide by, " +
                "and where any legal issues should be resolved. This text is inserted " +
                "into the Jurisdiction section of your ", termsOfUseLink, " page.") })));
  }
});



var CustomizePanel = createComponent({
  mixins: [SaveSettingMixin],

  componentDidMount: function() {
    Server.loadSettings('WholeSite', null, settings => {
      this.setState(settings);
    });
  },

  render: function() {
    if (!this.state)
      return r.p({}, 'Loading...');

    var settings = this.state;
    var saveSetting = this.saveSetting;

    return (
      r.div({},
        Setting({ setting: settings.horizontalComments, onSave: saveSetting,
          label: 'Horizontal comments', help: "Shall comments be laid out horizontally, " +
            "in two dimensions? If not, they're shown in a single column, " +
            "which is how all other forum and blog software work." }),

        Setting({ setting: settings.headerHtml, onSave: saveSetting, label: 'Header HTML',
          multiline: true, help: 'Any header, will be shown at the top of the page.' }),

        Setting({ setting: settings.footerHtml, onSave: saveSetting, label: 'Footer HTML',
          multiline: true, help: 'Any footer, shown at the bottom of the page.' }),

        Setting({ setting: settings.headStylesHtml, onSave: saveSetting, label: 'Styles HTML',
          multiline: true, help: 'Stylesheet link tags that will be inserted after ' +
              'other stylesheet tags in the <head> tag.' }),

        Setting({ setting: settings.headScriptsHtml, onSave: saveSetting, label: 'Scripts HTML',
          multiline: true, help: 'Script tags that will be inserted after other ' +
              'scripts in the <head> tag.' }),

        Setting({ setting: settings.endOfBodyHtml, onSave: saveSetting, label: '</body> HTML',
          multiline: true, help: 'Tags that will be inserted just before ' +
              'the end of the <body> tag.' }),

        Setting({ setting: settings.socialLinksHtml, onSave: saveSetting,
          label: 'Social links HTML', multiline: true,
          help: "Google+, Facebook, Twitter like and share buttons. Don't forget " +
              "to include a script too, e.g. in the <i>Scripts HTML</i> config value. " +
              "— Perhaps I'll remove this config value in the future, so you might " +
              "be better off not using it." }),

        SpecialContent({ contentId: '_stylesheet', label: 'Stylesheet',
            help: "CSS for this site. CSS means Cascading Style Sheets and " +
                "you use it to describe the look and formatting of this site." })));
  }
});


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list