/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * You may not modify, use, reproduce, or distribute this software
 * except in compliance with the terms of the license at:
 * http://developer.sun.com/berkeley_license.html
 */

package controller;

import business.cart.ShoppingCart;
import business.category.CategoryDao;
import business.customer.Customer;
import business.customer.CustomerDao;
import business.order.CustomerOrder;
import business.order.CustomerOrderDao;
import business.order.CustomerOrderDetails;
import business.order.CustomerOrderLineItem;
import business.order.CustomerOrderService;
import business.product.ProductDao;
import business.category.Category;
import business.product.Product;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import validate.Validator;

/**
 *
 * @author tgiunipero
 */
@WebServlet(name = "Controller",
            loadOnStartup = 1,
            urlPatterns = {"/category",
                           "/addToCart",
                           "/viewCart",
                           "/updateCart",
                           "/checkout",
                           "/purchase",
                           "/chooseLanguage"})
public class ControllerServlet extends HttpServlet {

    private String surcharge;

    private CategoryDao categoryDao;
    private ProductDao productDao;
    private CustomerOrderService customerOrderService;


    @Override
    public void init(ServletConfig servletConfig) throws ServletException {

        super.init(servletConfig);

        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {

            System.out.println("Error loading database driver: "+e.getMessage());
            System.exit(1);
        }

        // initialize servlet with configuration information
        surcharge = servletConfig.getServletContext().getInitParameter("deliverySurcharge");

        ApplicationContext applicationContext = ApplicationContext.INSTANCE;
        categoryDao = applicationContext.getCategoryDao();
        productDao = applicationContext.getProductDao();
        customerOrderService = applicationContext.getCustomerOrderService();


        // store category list in servlet context
        getServletContext().setAttribute("categories", applicationContext.getCategoryDao().findAll());
    }


    /**
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userPath = request.getServletPath();
        HttpSession session = request.getSession();
        Category selectedCategory;
        Collection<Product> categoryProducts;


        // if category page is requested
        if (userPath.equals("/category")) {

            // get categoryId from request
            String categoryId = request.getQueryString();

            if (categoryId != null) {

                // get selected category
                selectedCategory = categoryDao.findByCategoryId(Short.parseShort(categoryId));

                // place selected category in session scope
                session.setAttribute("selectedCategory", selectedCategory);

                // get all products for selected category
                categoryProducts = selectedCategory.getProducts();

                // place category products in session scope
                session.setAttribute("categoryProducts", categoryProducts);
            }


        // if business.cart page is requested
        } else if (userPath.equals("/viewCart")) {

            String clear = request.getParameter("clear");

            if ((clear != null) && clear.equals("true")) {

                ShoppingCart cart = (ShoppingCart) session.getAttribute("business/cart");
                cart.clear();
            }

            userPath = "/cart";


        // if checkout page is requested
        } else if (userPath.equals("/checkout")) {

            ShoppingCart cart = (ShoppingCart) session.getAttribute("business/cart");

            // calculate total
            cart.calculateTotal(surcharge);

            // forward to checkout page and switch to a secure channel


        // if user switches language
        } else if (userPath.equals("/chooseLanguage")) {

            // get language choice
            String language = request.getParameter("language");

            // place in request scope
            request.setAttribute("language", language);

            String userView = (String) session.getAttribute("view");

            if ((userView != null) &&
                (!userView.equals("/index"))) {     // index.jsp exists outside 'view' folder
                                                    // so must be forwarded separately
                userPath = userView;
            } else {

                // if previous view is index or cannot be determined, send user to welcome page
                try {
                    request.getRequestDispatcher("/index.jsp").forward(request, response);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return;
            }
        }

        // use RequestDispatcher to forward request internally
        String url = "/WEB-INF/view" + userPath + ".jsp";

        try {
            request.getRequestDispatcher(url).forward(request, response);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    /**
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");  // ensures that user input is interpreted as
                                                // 8-bit Unicode (e.g., for Czech characters)

        String userPath = request.getServletPath();
        HttpSession session = request.getSession();
        ShoppingCart cart = (ShoppingCart) session.getAttribute("cart");
        Validator validator = new Validator();


        // if addToCart action is called
        if (userPath.equals("/addToCart")) {

            // if user is adding item to cart for first time
            // create cart object and attach it to user session
            if (cart == null) {

                cart = new ShoppingCart();
                session.setAttribute("cart", cart);
            }

            // get user input from request
            String productId = request.getParameter("productId");

            if (!productId.isEmpty()) {
                final int id = Integer.parseInt(productId);
                Product product = productDao.findByProductId(id);
                cart.addItem(id, product.getPrice());
            }

            userPath = "/category";


        // if updateCart action is called
        } else if (userPath.equals("/updateCart")) {

            // get input from request
            String productId = request.getParameter("productId");
            String quantity = request.getParameter("quantity");

            boolean invalidEntry = validator.validateQuantity(productId, quantity);

            if (!invalidEntry) {

                Product product = productDao.findByProductId((Integer.parseInt(productId)));
                cart.update(product.getProductId(), quantity);
            }

            userPath = "/cart";


        // if purchase action is called
        } else if (userPath.equals("/purchase")) {

            if (cart != null) {

                // extract user data from request
                String name = request.getParameter("name");
                String email = request.getParameter("email");
                String phone = request.getParameter("phone");
                String address = request.getParameter("address");
                String cityRegion = request.getParameter("cityRegion");
                String ccNumber = request.getParameter("creditcard");

                // validate user data
                boolean validationErrorFlag = false;
                validationErrorFlag = validator.validateForm(name, email, phone, address, cityRegion, ccNumber, request);

                // if validation error found, return user to checkout
                if (validationErrorFlag == true) {
                    request.setAttribute("validationErrorFlag", validationErrorFlag);
                    userPath = "/checkout";

                    // otherwise, save order to database
                } else {

                    long orderId = customerOrderService.placeOrder(name, email, phone, address, cityRegion, ccNumber, cart);

                    // if order processed successfully send user to confirmation page
                    if (orderId != 0) {

                        // in case language was set using toggle, get language choice before destroying session
                        Locale locale = (Locale) session.getAttribute("javax.servlet.jsp.jstl.fmt.locale.session");
                        String language = "";

                        if (locale != null) {

                            language = (String) locale.getLanguage();
                        }

                        // dissociate shopping cart from session
                        cart = null;

                        // end session
                        session.invalidate();

                        if (!language.isEmpty()) {                       // if user changed language using the toggle,
                                                                         // reset the language attribute - otherwise
                            request.setAttribute("language", language);  // language will be switched on confirmation page!
                        }

                        // get order details
                        CustomerOrderDetails details = customerOrderService.getOrderDetails(orderId);

                        // place order details in request scope
                        request.setAttribute("customer", details.getCustomer());
                        request.setAttribute("products", details.getProducts());
                        request.setAttribute("orderRecord", details.getCustomerOrder());
                        request.setAttribute("orderedProducts", details.getCustomerOrderLineItems());

                        userPath = "/confirmation";

                    // otherwise, send back to checkout page and display error
                    } else {
                        userPath = "/checkout";
                        request.setAttribute("orderFailureFlag", true);
                    }
                }
            }
        }

        // use RequestDispatcher to forward request internally
        String url = "/WEB-INF/view" + userPath + ".jsp";

        try {
            request.getRequestDispatcher(url).forward(request, response);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
